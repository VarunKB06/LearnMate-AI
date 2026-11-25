# LearnMate AI 

On-device personalised learning assistant for Android (ARM Bharat Challenge, PS4, Education). It understands a learner's goal, finds skill gaps, recommends pathways, generates and adapts week-by-week study plans, and uses a **Llama model running on the phone through ExecuTorch** for reasoning and explanations. Also includes 15 subjects, a working Verilog simulator (HDL Studio) and adaptive quizzes.
ARM Bharat Challenge. Package `com.varunkb.learnmateai` · Kotlin · Jetpack Compose · Room · minSdk 28 · compile/targetSdk 37 · Java 17.

**The app declares no INTERNET permission and has no API keys.** Status of every feature: see [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).
**V4 adds** the Llama + ExecuTorch layer, goal understanding and adaptive study plans. The app, including the V4 Llama features, has been tested on a physical Vivo Y20G. To reproduce: see [docs/LLAMA_EXECUTORCH_SETUP.md](docs/LLAMA_EXECUTORCH_SETUP.md) and [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

## Submission links
| Deliverable | Where |
|---|---|
| Source code | This repository |
| Application (APK) | GitHub Releases: [<ADD RELEASE LINK>](https://github.com/VarunKB06/LearnMate-AI/releases/tag/v1.0.0) (the Llama model is installed separately, see [setup guide](docs/LLAMA_EXECUTORCH_SETUP.md)) |
| Demonstration video | <https://www.youtube.com/shorts/Qht6ll5SXkA> |
| Technical report | [docs/TECHNICAL_REPORT.pdf](docs/TECHNICAL_REPORT.pdf) (source: [docs/TECHNICAL_REPORT.md](docs/TECHNICAL_REPORT.md)) |

**Author:** Varun K B, Atria Institute of Technology. Tested on a Vivo Y20G (Arm, 6 GB RAM) with Llama 3.2 1B Instruct through ExecuTorch.

## PS4 requirements at a glance
| Requirement | Where |
|---|---|
| Understand learner goals | Plan screen: free-text goal read by Llama (JSON, validated), keyword fallback |
| Identify skill gaps | Bayesian Knowledge Tracing per topic, adaptive quizzes |
| Recommend learning pathways | Learning paths + goal-driven subject order with prerequisites |
| Generate study plans | `StudyPlanner`: week-by-week, fits hours/week, checkpoint quizzes |
| Adapt over time | Plan rebuilt after every quiz or finished lesson; revision added for weak topics |
| Llama on-device with ExecuTorch | `llm/ExecuTorchBackend.kt`, model installed via Model screen |
| Arm Android phone | arm64-v8a build, Vivo Y20G target |

## Bottom tabs
Home · Learn (catalogue → subject → lesson) · Studio (HDL Studio) · Tutor · Progress. Practice quizzes start from Home, a subject page or a lesson.

## What is inside
- **Learn**: 118 lessons in 15 subjects, grouped into learning paths (Programming Foundations, Computer Systems, Digital Design and VLSI, AI and ML). Each lesson has objectives, key terms, example, diagram, common mistakes, practice, summary; progress and bookmarks persist in Room.
- **HDL Studio**: Verilog editor (highlighting, line numbers, auto-indent, format), multi-file projects, compile, run, console, waveform, VCD export. Verilog lessons open a runnable demo.
- **Tutor (offline)**: BM25 retrieval with sources, follow-ups ("give an example", "explain simply", "in depth"), compare two concepts, practice questions, line-by-line Verilog explanation, compile-error and simulation-output explanation, study recommendation. Says "I don't have a reliable answer" instead of guessing. Works without any model.
- **Llama on-device (ExecuTorch)**: when a Llama 3.2 `.pte` file is installed, it explains the retrieved lesson (grounded, lesson text only), reads the learner's goal, advises what to study next and writes coaching notes. Falls back to the rule-based tutor on any failure.
- **Goal + study plan**: Plan screen from the dashboard. Adaptive, explainable, stored locally.
- **Quizzes + mastery**: 411 questions, adaptive 10-question quizzes, Bayesian Knowledge Tracing per topic, dashboard and progress screens.
- **Diagnostics**: in-app benchmark screen (About/Diagnostics).

## Build
1. Open the folder in Android Studio, let Gradle sync (internet needed once).
2. Connect the phone with USB debugging, press Run. Or `gradlew.bat assembleDebug` / `testDebugUnitTest` / `connectedDebugAndroidTest`.
3. Existing data is kept: the database schema is unchanged in V4 (goal and plan use a separate preferences file).
4. Install the Llama model: [docs/LLAMA_EXECUTORCH_SETUP.md](docs/LLAMA_EXECUTORCH_SETUP.md).

Hardware: any Android 6.0+ phone; target test device Vivo Y20G (6 GB). APK stays small because the 1 GB model is installed separately (adb push or in-app import), see the setup guide. Dev machine: 8 GB RAM is enough for Android Studio; Gradle needs roughly 2 GB heap.

Licences: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
