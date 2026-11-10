package com.varunkb.learnmateai.hdl

/** Public entry point: compile and simulate a set of Verilog source files. */
object VerilogSimulator {

    private fun parseAll(files: List<SourceFile>, diags: MutableList<Diagnostic>): Pair<List<ModuleDef>, Boolean> {
        val mods = ArrayList<ModuleDef>()
        var parseOk = true
        for (f in files) {
            try {
                val text = VPreprocessor.run(f.text)
                mods.addAll(VParser(f.name, VLexer.lex(text)).parseAll())
            } catch (e: VError) {
                parseOk = false
                diags.add(Diagnostic(Severity.Error, f.name, e.line, e.col, e.message ?: "syntax error", e.hint))
            } catch (e: StackOverflowError) {
                parseOk = false
                diags.add(Diagnostic(Severity.Error, f.name, 0, 0, "the source is nested too deeply to parse"))
            }
        }
        return mods to parseOk
    }

    private fun topsOf(mods: List<ModuleDef>): List<String> {
        val used = HashSet<String>()
        for (m in mods) for (i in m.items) if (i is InstItem) used.add(i.module)
        return mods.map { it.name }.filter { it !in used }.distinct()
    }

    fun compile(files: List<SourceFile>): CompileResult {
        val diags = ArrayList<Diagnostic>()
        if (files.isEmpty() || files.all { it.text.isBlank() }) {
            diags.add(Diagnostic(Severity.Error, "project", 0, 0, "there is no Verilog code to compile", "Write a module in the editor first."))
            return CompileResult(false, diags, emptyList())
        }
        val (mods, parseOk) = parseAll(files, diags)
        if (parseOk) VChecker.check(mods, diags)
        val ok = diags.none { it.severity == Severity.Error }
        return CompileResult(ok, diags, if (ok) topsOf(mods) else emptyList())
    }

    fun simulate(files: List<SourceFile>, limits: SimLimits = SimLimits(), cancelled: () -> Boolean = { false }): SimResult {
        val t0 = System.nanoTime()
        fun ms() = (System.nanoTime() - t0) / 1_000_000
        val diags = ArrayList<Diagnostic>()
        val cr: CompileResult
        val mods: List<ModuleDef>
        try {
            if (files.isEmpty() || files.all { it.text.isBlank() }) {
                cr = compile(files)
                return SimResult(cr, emptyList(), "not run", 0, 0, ms(), null)
            }
            val (m, parseOk) = parseAll(files, diags)
            mods = m
            if (parseOk) VChecker.check(mods, diags)
            val ok = diags.none { it.severity == Severity.Error }
            cr = CompileResult(ok, diags, if (ok) topsOf(mods) else emptyList())
        } catch (e: Exception) {
            val c = CompileResult(false, listOf(Diagnostic(Severity.Error, "project", 0, 0, "internal compiler error: ${e.message}")), emptyList())
            return SimResult(c, emptyList(), "not run", 0, 0, ms(), null)
        }
        if (!cr.ok) return SimResult(cr, emptyList(), "not run", 0, 0, ms(), null)

        val byName = LinkedHashMap<String, ModuleDef>()
        for (m in mods) byName[m.name] = m
        val tops = cr.topModules.mapNotNull { byName[it] }
        if (tops.none { t -> t.items.any { it is ProcItem } }) {
            return SimResult(cr, listOf("no testbench (nothing to run)"), "not run", 0, 0, ms(), null)
        }
        val engine = Engine(byName, limits, cancelled)
        var by: String
        try {
            engine.elaborate(tops)
            by = engine.run()
        } catch (e: RtError) {
            engine.output.add("ERROR: ${e.message}" + if (e.line > 0) " (line ${e.line})" else "")
            by = "runtime error"
        } catch (e: StopSim) {
            by = e.reason
        } catch (e: StackOverflowError) {
            engine.output.add("ERROR: statements are nested too deeply")
            by = "runtime error"
        } catch (e: Exception) {
            engine.output.add("ERROR: internal simulator error: ${e.message}")
            by = "runtime error"
        }
        engine.flushPartial()
        val wave = try { engine.waveform(engine.now) } catch (e: Exception) { null }
        return SimResult(cr, engine.output.toList(), by, engine.now, engine.steps, ms(), wave)
    }
}
