# LearnMate AI (V3)

Offline-first learning app for Android: lesson catalogue, 15 subjects, a working Verilog simulator (HDL Studio), an offline tutor, adaptive quizzes and mastery tracking.
ARM Bharat Challenge. Package `com.varunkb.learnmateai` · Kotlin · Jetpack Compose · Room · minSdk 23 · compile/targetSdk 37 · Java 17.

**The app declares no INTERNET permission and has no API keys.** Status of every feature: see [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).
**Important:** the Android layer has not been compiled or run on a device by the author; see [docs/TESTING_REPORT.md](docs/TESTING_REPORT.md).

## Bottom tabs
Home · Learn (catalogue → subject → lesson) · Studio (HDL Studio) · Tutor · Progress. Practice quizzes start from Home, a subject page or a lesson.

## What is inside
- **Learn**: 118 lessons in 15 subjects, grouped into learning paths (Programming Foundations, Computer Systems, Digital Design and VLSI, AI and ML). Each lesson has objectives, key terms, example, diagram, common mistakes, practice, summary; progress and bookmarks persist in Room.
- **HDL Studio**: Verilog editor (highlighting, line numbers, auto-indent, format), multi-file projects, compile, run, console, waveform, VCD export. Verilog lessons open a runnable demo.
- **Tutor (offline)**: BM25 retrieval with sources, follow-ups ("give an example", "explain simply", "in depth"), compare two concepts, practice questions, line-by-line Verilog explanation, compile-error and simulation-output explanation, study recommendation. Says "I don't have a reliable answer" instead of guessing. Not a generative AI.
- **Quizzes + mastery**: 411 questions, adaptive 10-question quizzes, Bayesian Knowledge Tracing per topic, dashboard and progress screens.
- **Diagnostics**: in-app benchmark screen (About/Diagnostics).

## Build
1. Open the folder in Android Studio, let Gradle sync (internet needed once).
2. Connect the phone with USB debugging, press Run. Or `gradlew.bat assembleDebug` / `testDebugUnitTest` / `connectedDebugAndroidTest`.
3. Existing data from V1 is kept (Room migration 1→2 only adds tables/columns).

Hardware: any Android 6.0+ phone; target test device Vivo Y20G (6 GB). APK is small (content ≈ 1 MB JSON, no ML model). Dev machine: 8 GB RAM is enough for Android Studio; Gradle needs roughly 2 GB heap.

Licences: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
