# LearnMate AI: on-device personalised learning assistant
ARM Bharat Challenge, PS4 (Education). Author: Varun K B.

## 1. Problem and approach
Learners need a path that fits their goal, current skill and time, and that changes as they learn. LearnMate runs entirely on an Arm Android phone: a Llama model (ExecuTorch) reasons about goals and explains lessons, while transparent learner-modelling and planning rules make the decisions that must be reliable.

## 2. Architecture
Everything runs on the phone. Main path for a tutor question: question -> BM25 lesson retrieval (rules) -> grounded prompt -> Llama 3.2 1B through ExecuTorch -> answer. If no model is installed, or the model fails or is stopped, the rule-based answer is shown instead. Goal path: free-text goal -> Llama JSON -> validation -> planner -> week-by-week plan, rebuilt after each quiz or lesson.
- UI: Jetpack Compose. State: `MainViewModel`. Storage: Room (lessons progress, quizzes, mastery, chat) and a preferences file (goal, plan).
- Learner model: BKT per topic. Retrieval: BM25 over 118 lessons. Planner: `StudyPlanner`.
- LLM layer: `LlmManager` (lifecycle, one generation at a time, cancel) -> `ExecuTorchBackend` (`LlmModule`, XNNPACK) -> `LlmAdvisor` (prompts and validation) -> UI.

## 3. How each PS4 capability is met
| Capability | Implementation |
|---|---|
| Understand goals | Llama converts free text to JSON (goal type, subjects, interests, hours, weeks, level). The JSON is validated against the subject list; invalid output falls back to a keyword parser |
| Identify skill gaps | BKT mastery per topic from adaptive quizzes |
| Recommend pathways | Goal subjects ordered with unfinished prerequisites first; weak topics inserted as revision |
| Generate study plans | Week-by-week plan within hours/week, one checkpoint quiz per week, spare time for interests |
| Adapt over time | Plan rebuilt after each quiz or lesson; notes say what changed; coaching note regenerated on request |

## 4. On-device Llama with ExecuTorch
- Model: Llama 3.2 1B Instruct, SpinQuant INT4 (EO8) build exported for ExecuTorch with the XNNPACK backend (`Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte`, from the `executorch-community` Hugging Face releases). File size: 1083 MB. App sequence budget: 1024 tokens.
- Runtime: `org.pytorch:executorch-android` (version pinned in `gradle/libs.versions.toml`), XNNPACK CPU backend.
- Prompting: Llama 3 chat template; retrieval-grounded tutoring; JSON-only goal extraction; temperature 0.3.
- Safety nets: output validation, fallback to rule-based tutor, Stop button, model can be switched off.

## 5. Measurements (Model screen self-test, run on the phone; the saved report is in Diagnostics)
| Metric | Value |
|---|---|
| Device | Vivo Y20G (Arm, arm64-v8a), 6 GB RAM |
| Model load time | 12,014 ms |
| First-token latency (self-test prompt, 39 prompt tokens) | 4,336 ms (first run, includes warm-up) |
| Decode speed | 5.63 tokens/s (64 tokens in 15.5 s) |
| Typical tutor answer time (lesson-grounded, stopwatch, 5 questions) | about 60 s |
| Goal-to-plan time with Llama | about 30 s |
| Memory, heat, stability | Model file 1,083 MB; RAM use not measured. In a 5-minute session (3 tutor questions, 1 plan) battery went 50% to 49%, no noticeable heat, no lag or crashes |
| APK size (without model) | 16.0 MB release (R8-shrunk, arm64-v8a, includes the ExecuTorch runtime); 44 MB debug |

## 6. Evaluation
- JVM unit tests (`gradlew testDebugUnitTest`): all pass. They cover BKT, quiz engine, retrieval, study planner (prerequisite order, hour budget, skipping mastered or completed lessons, revision for weak topics, re-planning), goal parser (including validation of Llama JSON), prompt formatting and plan serialisation.
- Retrieval top-1 accuracy: 97.4% on 38 author-written queries (see TESTING_AND_BENCHMARKS.md).
- On-device tutor check (Vivo Y20G): 5 lesson questions asked, 5 of 5 answers judged correct against the lesson by the author, about 60 s each.
- On-device goal check: 5 goals covering a career goal (VLSI design verification), an exam goal (DBMS), software placements, a beginner Python goal, and an FPGA goal with typed hours and weeks. All 5 generated plans were judged sensible by the author, about 30 s each.
- Limits of this evaluation: one tester (the author), small samples, no formal accuracy metric for Llama answers, one device.

## 7. Privacy
No INTERNET permission, no analytics, no API keys. The model, prompts, goals and progress stay on the phone.

## 8. Limitations and future work
Small-model errors; speed on mid-range CPUs; lesson content not expert-reviewed; no NPU/GPU delegate (XNNPACK CPU only); streaming output and multi-turn memory for the model are future work.
