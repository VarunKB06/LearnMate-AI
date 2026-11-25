# AI design (V4)

LearnMate has two AI layers. The first always works. The second is the Llama model required by PS4 and runs on the phone through ExecuTorch.

## Layer 1: learner model and planning (no model needed)
1. **Bayesian Knowledge Tracing (BKT)** keeps a probability of mastery per topic and updates it after every answer. This identifies skill gaps.
2. **Adaptive question selection** picks quiz questions from weak topics first.
3. **Retrieval tutor (BM25)** finds the best lesson for a question, reports a match score and says it does not know when nothing matches.
4. **Goal parser (rules)** extracts subjects, hours, weeks, level and goal type from free text. Used when no model is installed or the model output is invalid.
5. **Study planner** builds the schedule: goal subjects in priority order, unfinished prerequisites first, weak topics as revision, mastered or completed lessons skipped, lessons packed into weeks up to hours/week with one checkpoint quiz per week, spare time given to the learner's interests. It is rebuilt after every quiz or finished lesson, with notes on what changed.

## Layer 2: Llama on-device (ExecuTorch)
Runtime: `org.pytorch:executorch-android`, class `LlmModule`, XNNPACK CPU backend. Model: Llama 3.2 1B Instruct (or 3B on stronger phones), exported to `.pte` with 8da4w quantisation. Installed separately from the APK (see LLAMA_EXECUTORCH_SETUP.md).

| Job | Input given to Llama | Output use |
|---|---|---|
| Understand goal | Learner's words + list of subject ids | JSON, validated against the catalogue; invalid output is discarded |
| Grounded tutoring | Top BM25 lesson text + learner summary | Plain-text answer limited to the lesson |
| Advice ("what should I study?") | Goal, weak topics, this week's plan, subject list | Short numbered steps |
| Coaching note | Goal, weak topics, this week's items | 90-word note stored with the plan |

Design choices that make a small model dependable:
- **Retrieval first, generation second.** The model never answers from memory for concept questions.
- **Structured output is validated.** A hallucinated subject id or number cannot enter the plan.
- **The schedule is deterministic.** Llama explains the plan; it does not decide it, so a plan is always valid and testable.
- **Graceful fallback.** If the model is missing, fails, is stopped or is switched off, the rule-based answer is shown instead, with a note.
- **Low temperature (0.3)** and short answers (about 220 tokens) to limit errors and latency.
- **Privacy.** No internet permission; prompts and answers never leave the phone.

## Adaptation loop
Quiz answers update BKT, which changes the next quiz, the revision items and the plan. The learner can also edit the goal at any time. A new goal builds a new plan; finishing a quiz or lesson rebuilds the current one.

## What is not claimed
- Speed and accuracy of the Llama model on the Vivo Y20G are measured by the in-app self-test and must be reported from your own run.
- Lesson content has not been reviewed by a subject expert.
