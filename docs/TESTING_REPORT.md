# Testing report (only what was actually run)

## Run in the build sandbox (Linux, no Android SDK)
Command: `kt-test.sh` (Kotlin 2.0.21 compiler from Gradle's cache + JUnit 4.13.2) over `domain/` and `hdl/` sources and their tests.
Result: **OK (114 tests)**. Includes: BKT, quiz engine, content packs, tutor retrieval and skills, curriculum logic, Verilog lexer/parser/simulator (gates, adders, flip-flops, counter, FSM, memories, error and limit cases, fuzz), formatter, templates, and every lesson's Verilog demo compiling and printing PASS.
Retrieval accuracy on 38 author-written queries: top-1 97.4% (one miss: "why does 7/2 give 3").
Content validator (`validate_content.py`): OK on all 15 packs.
Migration SQL: executed with Python sqlite3 against a v1-shaped database (not through Room).

## NOT run
- Gradle sync / `assembleDebug` / `assembleRelease` (no Android SDK or Maven here).
- Room KSP generation, Compose compilation, `connectedDebugAndroidTest`.
- Anything on a Vivo Y20G. No device performance, battery or memory numbers exist for V3.

## You should run
`gradlew testDebugUnitTest`, then Run on the phone, then `gradlew connectedDebugAndroidTest`. If sync fails on versions, adjust only the `ksp`/`room` lines in `gradle/libs.versions.toml`. Report compile errors back and they can be fixed quickly.

## V4 update (built and run on a physical phone)
- The Android app, including the V4 additions (llm package, Plan and Model screens), builds in Android Studio and runs on a Vivo Y20G.
- Llama 3.2 1B Instruct (SpinQuant INT4, 1083 MB) loads and answers through ExecuTorch: load 12,014 ms, first token 4,336 ms, 5.63 tokens/s (self-test, 64 tokens in 15.5 s).
- Not run or not reported: instrumented UI tests, long-session and battery tests, other devices, formal accuracy evaluation of Llama answers.
- `gradlew testDebugUnitTest`: all unit tests pass, including the new planner, goal-parser and prompt tests.
- Release build (`assembleRelease`, R8 shrinking on) produced a 16.0 MB APK.
