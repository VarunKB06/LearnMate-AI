package com.varunkb.learnmateai

import com.varunkb.learnmateai.data.HdlFileEntity
import com.varunkb.learnmateai.data.HdlProjectEntity
import com.varunkb.learnmateai.data.LearnRepository
import com.varunkb.learnmateai.domain.Lesson
import com.varunkb.learnmateai.hdl.Diagnostic
import com.varunkb.learnmateai.hdl.HdlTemplates
import com.varunkb.learnmateai.hdl.Severity
import com.varunkb.learnmateai.hdl.SimLimits
import com.varunkb.learnmateai.hdl.SourceFile
import com.varunkb.learnmateai.hdl.VerilogFormatter
import com.varunkb.learnmateai.hdl.VerilogSimulator
import com.varunkb.learnmateai.hdl.Waveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorFile(val id: Long, val name: String, val text: String, val isTestbench: Boolean)

/** kind: 0 normal, 1 error, 2 warning, 3 success */
data class ConsoleLine(val text: String, val kind: Int = 0)

data class HdlUiState(
    val projectId: Long? = null,
    val projectName: String = "",
    val lessonId: String? = null,
    val files: List<EditorFile> = emptyList(),
    val active: Int = 0,
    val console: List<ConsoleLine> = emptyList(),
    val busy: Boolean = false,
    val waveform: Waveform? = null,
    val message: String? = null
) {
    val activeFile: EditorFile? get() = files.getOrNull(active)
}

/** State and actions of HDL Studio. Projects and files are stored in Room; simulation runs off the main thread. */
class HdlController(private val repo: LearnRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HdlUiState())
    val state: StateFlow<HdlUiState> = _state.asStateFlow()
    val projects = repo.hdlProjects
    val templates get() = HdlTemplates.all

    private var saveJob: Job? = null
    private var runJob: Job? = null
    @Volatile private var cancelFlag = false

    // ---- projects ----
    fun openProject(id: Long) {
        scope.launch {
            val p = repo.hdlProject(id) ?: run { _state.update { it.copy(message = "That project no longer exists.") }; return@launch }
            val files = repo.hdlFiles(id).map { EditorFile(it.id, it.name, it.text, it.isTestbench) }
            _state.value = HdlUiState(projectId = id, projectName = p.name, lessonId = p.lessonId, files = files, active = 0)
        }
    }

    fun closeProject() {
        scope.launch {
            persistNow()
            cancelFlag = true
            _state.value = HdlUiState()
        }
    }

    fun newBlank() = createFrom(HdlTemplates.all.first { it.id == "blank" }.let { it.title.replace("Blank project", "Untitled") to it })

    fun createFromTemplate(id: String) {
        val t = HdlTemplates.all.firstOrNull { it.id == id } ?: return
        createFrom(t.title to t)
    }

    private fun createFrom(p: Pair<String, HdlTemplates.Template>) {
        val (name, t) = p
        scope.launch {
            val id = repo.createHdlProject(
                name, null,
                t.files.map { HdlFileEntity(projectId = 0, name = it.name, text = it.text, isTestbench = it.name in t.tbFileNames) }
            )
            openProject(id)
        }
    }

    /** Opens (or creates) the project that belongs to a lesson's runnable example. */
    fun openFromLesson(lesson: Lesson, onReady: () -> Unit) {
        val hdl = lesson.hdl ?: return
        scope.launch {
            val existing = repo.hdlProjectForLesson(lesson.id)
            val id = existing?.id ?: repo.createHdlProject(
                hdl.title, lesson.id,
                hdl.files.map { HdlFileEntity(projectId = 0, name = it.name, text = it.text, isTestbench = it.isTestbench) }
            )
            openProject(id)
            onReady()
        }
    }

    fun deleteProject(id: Long) {
        scope.launch {
            repo.deleteHdlProject(id)
            if (_state.value.projectId == id) _state.value = HdlUiState()
        }
    }

    fun renameProject(name: String) {
        val id = _state.value.projectId ?: return
        val n = name.trim().ifBlank { return }
        _state.update { it.copy(projectName = n) }
        scope.launch { repo.renameHdlProject(id, n) }
    }

    // ---- files ----
    fun select(i: Int) = _state.update { it.copy(active = i.coerceIn(0, maxOf(it.files.size - 1, 0))) }

    fun edit(text: String) {
        _state.update { s ->
            val f = s.activeFile ?: return@update s
            if (f.text == text) s else s.copy(files = s.files.mapIndexed { i, x -> if (i == s.active) x.copy(text = text) else x })
        }
        saveJob?.cancel()
        saveJob = scope.launch { delay(600); persistNow() }
    }

    private suspend fun persistNow() {
        val s = _state.value
        val pid = s.projectId ?: return
        repo.saveHdlFiles(pid, s.files.map { HdlFileEntity(it.id, pid, it.name, it.text, it.isTestbench) })
    }

    private fun validName(name: String, existing: List<EditorFile>, self: Long = -1): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter a file name."
        if (!Regex("[A-Za-z0-9_.-]+").matches(n)) return "Use only letters, digits, _ . and -"
        if (!(n.endsWith(".v") || n.endsWith(".sv"))) return "File names should end in .v or .sv"
        if (existing.any { it.id != self && it.name.equals(n, true) }) return "A file with that name already exists."
        return null
    }

    fun addFile(name: String, testbench: Boolean) {
        val s = _state.value
        val pid = s.projectId ?: return
        validName(name, s.files)?.let { m -> _state.update { it.copy(message = m) }; return }
        val template = if (testbench) "module ${name.trim().substringBeforeLast('.')};\n  initial begin\n    \$display(\"hello\");\n  end\nendmodule\n"
        else "module ${name.trim().substringBeforeLast('.')}();\n\nendmodule\n"
        scope.launch {
            val id = repo.addHdlFile(pid, name.trim(), template, testbench)
            _state.update { it.copy(files = it.files + EditorFile(id, name.trim(), template, testbench), active = it.files.size) }
        }
    }

    fun renameFile(name: String) {
        val s = _state.value
        val f = s.activeFile ?: return
        validName(name, s.files, f.id)?.let { m -> _state.update { it.copy(message = m) }; return }
        _state.update { st -> st.copy(files = st.files.map { if (it.id == f.id) it.copy(name = name.trim()) else it }) }
        scope.launch { repo.renameHdlFile(f.id, name.trim()) }
    }

    fun deleteActiveFile() {
        val s = _state.value
        val f = s.activeFile ?: return
        if (s.files.size <= 1) { _state.update { it.copy(message = "A project needs at least one file.") }; return }
        _state.update { st -> st.copy(files = st.files.filter { it.id != f.id }, active = 0) }
        scope.launch { repo.deleteHdlFile(f.id) }
    }

    fun setTestbench(flag: Boolean) {
        val f = _state.value.activeFile ?: return
        _state.update { st -> st.copy(files = st.files.map { if (it.id == f.id) it.copy(isTestbench = flag) else it }) }
        saveJob?.cancel(); saveJob = scope.launch { persistNow() }
    }

    fun format() {
        val f = _state.value.activeFile ?: return
        edit(VerilogFormatter.format(f.text))
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun clearConsole() = _state.update { it.copy(console = emptyList(), waveform = null) }

    // ---- compile / run ----
    private fun sources(): List<SourceFile> = _state.value.files.map { SourceFile(it.name, it.text) }

    private fun diagLines(ds: List<Diagnostic>): List<ConsoleLine> =
        ds.map { ConsoleLine(it.format(), if (it.severity == Severity.Error) 1 else 2) }

    fun compile() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, console = listOf(ConsoleLine("Compiling…"))) }
        val src = sources()
        runJob = scope.launch {
            persistNow()
            val r = withContext(Dispatchers.Default) { VerilogSimulator.compile(src) }
            val lines = ArrayList<ConsoleLine>()
            lines.addAll(diagLines(r.diagnostics))
            val errs = r.diagnostics.count { it.severity == Severity.Error }
            val warns = r.diagnostics.count { it.severity == Severity.Warning }
            lines.add(
                if (r.ok) ConsoleLine("Compilation succeeded ($warns warning(s)). Top modules: ${r.topModules.joinToString(", ").ifEmpty { "none" }}", 3)
                else ConsoleLine("Compilation failed: $errs error(s), $warns warning(s).", 1)
            )
            _state.update { it.copy(busy = false, console = lines, waveform = null) }
        }
    }

    fun run() {
        if (_state.value.busy) return
        cancelFlag = false
        _state.update { it.copy(busy = true, console = listOf(ConsoleLine("Compiling and running…"))) }
        val src = sources()
        runJob = scope.launch {
            persistNow()
            val r = withContext(Dispatchers.Default) { VerilogSimulator.simulate(src, SimLimits()) { cancelFlag } }
            val lines = ArrayList<ConsoleLine>()
            lines.addAll(diagLines(r.compile.diagnostics))
            if (!r.compile.ok) {
                lines.add(ConsoleLine("Compilation failed: ${r.compile.diagnostics.count { it.severity == Severity.Error }} error(s). Fix them and run again.", 1))
            } else {
                r.output.forEach { lines.add(ConsoleLine(it, if (it.startsWith("ERROR")) 1 else 0)) }
                val ok = r.finishedBy == "\$finish" || r.finishedBy == "event queue empty" || r.finishedBy == "not run"
                lines.add(
                    ConsoleLine(
                        if (r.finishedBy == "not run") "Nothing to run. Add a testbench with an initial block."
                        else "Simulation stopped: ${r.finishedBy} at time ${r.endTime} (${r.steps} steps, ${r.wallMillis} ms)" +
                            if (!ok) ". The safety limit stopped it; check for loops without delays or a clock that never stops." else ".",
                        if (ok) 3 else 2
                    )
                )
            }
            _state.update { it.copy(busy = false, console = lines, waveform = r.waveform) }
        }
    }

    fun stop() { cancelFlag = true }
}
