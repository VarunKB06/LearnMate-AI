# Verified vs unverified, known limitations (V3)

See IMPLEMENTATION_STATUS.md and docs/TESTING_REPORT.md for the current status. Test counts and lesson counts below were for V1; V3 has 114 JVM tests and 118 lessons.

## V3 additions to the limitations
- Android layer (catalogue, HDL Studio UI, tutor UI, Room v2) never compiled or run here; no Vivo test.
- Simulator is a Verilog subset: no function/task/generate/signed/inout; z is read as x; `always @(*)` runs once at t=0; multiple drivers resolve last-wins; max 64-bit values; limits 1,000,000 time units, 5,000,000 steps, 2000 output lines, 8 s.
- Python, DSA and DBMS lessons use the shorter V1 format.
- Tutor is rule-based; no LLM and no online research (not implemented).


## Verified (compiled and run in the build sandbox)
- All of `domain/` compiles (Kotlin 2.0.21) and 51 JUnit tests pass.
- All four JSON content packs parse and pass structural validation.
- Retrieval gold-set accuracy 97.4% (author-written queries).

## Not verified (could not be built here: no Android SDK, no Maven access)
- Gradle sync with the new dependencies, Room/KSP code generation, Compose compilation of `data/`, `perf/`, `ui/`, `MainViewModel`.
- Instrumented tests (Room DAO and Compose UI flow): written, never executed.
- Release build with R8/shrinking.
- Everything on the Vivo Y20G: start-up time, latency, memory, battery, temperature, UI smoothness. **No device numbers exist yet.**

An independent read-through for compile errors found no compile or Room problems (and one test bug, fixed). That is not the same as a compile.

## Most likely first-build problem: dependency versions
New versions chosen: Room **2.8.5**, KSP **2.3.6**, kotlinx-coroutines **1.10.2** (in `gradle/libs.versions.toml`).
The project uses AGP 9.5.1 with built-in Kotlin, which needs a KSP release that supports it (2.3.x does, per the KSP/AGP 9 notes).
If Gradle sync reports that a version cannot be found or KSP is incompatible with the Kotlin version, change only the `ksp`
(or `room`) line in `libs.versions.toml` to the version Android Studio suggests. No code change should be needed.

## Functional limitations
- **No generative AI / LLM / LiteRT model** (see AI design). The tutor answers only from its bundled lessons.
- BKT parameters are defaults, not trained on learner data.
- Mastery for imported legacy data is an estimate from aggregated counts.
- Questions and lessons are written for this project and have not been reviewed by a subject expert; please spot-check them before submitting.
- Quiz history shows score and date, not per-question drill-down (answers are stored in the database for future use).
- Release APK is signed with the debug key for easy installation; use your own keystore for any store release.
- Only English content; no accessibility audit beyond Compose defaults.

## Changes versus the verified `zipapp.zip`
- `MainActivity.kt` (652 lines holding everything) was split into ViewModel, repository and screens. Existing screens, wording and colours were kept; the old SharedPreferences data is migrated.
- Quiz is now 10 adaptively selected questions (the verified build had 12 C questions with a fixed first 5). Question count per subject is 30, as in the handover.
- Added: Room, 3 more subjects, mastery model, tutor upgrade (BM25), subject switching, diagnostics, About screen, tests, docs.
