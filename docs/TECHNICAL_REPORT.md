# LearnMate AI: on-device personalised learning assistant
ARM Bharat Challenge, PS4 (Education). Author: Varun K B.

## 1. Problem and approach
Learners need a path that fits their goal, current skill and time, and that changes as they learn. LearnMate runs entirely on an Arm Android phone: a Llama model (ExecuTorch) reasons about goals and explains lessons, while transparent learner-modelling and planning rules make the decisions that must be reliable.

## 2. Architecture
(Add the diagram from docs/ARCHITECTURE.md.)
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
- Model: Llama 3.2 1B Instruct, exported with XNNPACK, 8da4w quantisation, sequence length 1024. File size: ____ MB.
- Runtime: `org.pytorch:executorch-android` ____ (version used).
- Prompting: Llama 3 chat template; retrieval-grounded tutoring; JSON-only goal extraction; temperature 0.3.
- Safety nets: output validation, fallback to rule-based tutor, Stop button, model can be switched off.

## 5. Measurements (fill in from the Model screen self-test on the phone)
| Metric | Value |
|---|---|
| Device | Vivo Y20G, ____ GB RAM, Android ____ |
| Model load time | ____ ms |
| First-token latency (self-test prompt) | ____ ms |
| Decode speed | ____ tokens/s |
| Typical tutor answer time | ____ s |
| Goal-to-plan time with Llama | ____ s |
| Peak memory / temperature notes | ____ |
| APK size (without model) | ____ MB |

## 6. Evaluation
- JVM unit tests: BKT, quiz engine, retrieval, planner, goal parser, prompt formatting (`gradlew testDebugUnitTest`).
- Retrieval top-1 accuracy: 97.4% on 38 author-written queries (see TESTING_AND_BENCHMARKS.md).
- Add: goal-parsing accuracy on ~10 sample goals (Llama vs rules), and 5 tutor answers checked by hand against their lessons.

## 7. Privacy
No INTERNET permission, no analytics, no API keys. The model, prompts, goals and progress stay on the phone.

## 8. Limitations and future work
Small-model errors; speed on mid-range CPUs; lesson content not expert-reviewed; no NPU/GPU delegate (XNNPACK CPU only); streaming output and multi-turn memory for the model are future work.
