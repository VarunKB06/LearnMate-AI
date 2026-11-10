# AI design and feasibility

## What runs in the app today

### 1. Bayesian Knowledge Tracing (BKT): student model
Each topic keeps `P(known)`. After each answer the app applies Bayes' rule with four parameters
(initial 0.30, learn 0.12, slip 0.10, guess 0.25), then the learning transition.
Example (unit-tested): `P=0.30`, correct answer → `0.6539`.
It drives (a) which topics the next quiz favours, (b) the mastery bars, (c) the "revise this" recommendation.
**Limitation:** parameters are sensible defaults for 4-option MCQs, not fitted to real learner data.

### 2. Adaptive question selection
Weighted sampling without replacement. Weight = topic need `(0.5 + 2.5·(1 − P))` × difficulty fit × recency penalty
(0.15 if seen in the last 3 attempts), with a per-topic cap so quizzes stay varied.
Unit tests show weak topics are sampled more, recent questions less, and no topic exceeds 3 of 10.

### 3. Retrieval tutor (BM25)
Lessons are indexed with field weights (title ×3, keywords ×3, topic ×2, body ×1), light stemming and a small
abbreviation list. A reply is given only when the idf-weighted **coverage** of the query reaches 40%; otherwise the tutor says
it does not know. Replies show the source lesson and match score. Progress and study-plan questions are answered from BKT data.
The tutor also answers honestly when asked whether it is an LLM.

**Measured retrieval quality (JVM, 38 hand-written queries across the 4 subjects):** top-1 97.4% (37/38), top-3 97.4%.
Caveat: the query set was written by the same author as the lessons, so treat this as a regression check, not an
independent benchmark. The single miss is `why does 7/2 give 3` (digits are discarded as tokens).

## What does NOT run

No generative model, no neural network, no LiteRT/TFLite model, no quantisation. These were **not** implemented, because:
- no model file could be obtained or tested in the build environment (no Android SDK, no network), and
- the rules for this project forbid claiming AI features that were not tested on a device.

## Is an on-device LLM realistic on the Vivo Y20G? (assessment, not measured)

Hardware: Helio G80 (2× Cortex-A75 + 6× Cortex-A55, Mali-G52 MC2), 6 GB RAM, Android 13.
- A small instruction-tuned model (about 0.5–1B parameters, 4-bit quantised, roughly 0.4–0.8 GB file) is **feasible to load**
  with Google's MediaPipe LLM Inference API / LiteRT-LM on CPU.
- Expect **slow generation** (a few tokens per second at best on A55-heavy CPUs) and noticeable heat; the GPU path on Mali-G52
  is unlikely to help. These are expectations from the hardware class, **not measurements**; measure on the phone before promising anything.
- Vivo's background-process limits mean the model must be loaded on demand and released afterwards.
- Quality of 0.5–1B models on programming Q&A is limited; they hallucinate. A retrieval-grounded design (feed the retrieved lesson to
  the model and ask it to rephrase) is the safe way to use one.
- The model file cannot reasonably live inside the APK. Options: user-downloaded file placed in app storage (needs INTERNET only
  for the one-time download; keep that out of the main app or behind an explicit opt-in), or Play Asset Delivery.

## How to add a real model later (recommended path)

1. Keep `RetrievalTutorEngine` as the default and fallback. It implements `TutorEngine`.
2. Add `LlmTutorEngine : TutorEngine` that retrieves the top lesson with `Bm25Index`, builds a prompt
   "Answer using only this lesson: …", and calls the MediaPipe LLM Inference task.
3. Load the model lazily off the main thread; cap output length; cancel on screen exit.
4. Use the existing **Diagnostics** screen as the template to measure time-to-first-token, tokens/s, PSS and temperature on the Y20G.
5. Note: MediaPipe tasks-genai is documented as needing minSdk 24 (this app is 23); verify, then raise minSdk or isolate it in a separate flavour.
6. Another lighter option: a TFLite sentence-embedding model for semantic retrieval (tens of MB). Implement behind the same interface and
   compare against BM25 on the gold query set in `TutorEngineTest`.

## How to describe this in the submission (suggested wording)
"LearnMate AI is an offline adaptive learning app. It uses on-device Bayesian Knowledge Tracing to model learner mastery and
choose questions, and an on-device BM25 retrieval tutor over a curated lesson pack. It uses no cloud services and no generative model."

## V3 update: layers
- Layer 1 (implemented, verified on JVM): BM25 + rule-based skills (follow-ups, compare, practice, Verilog/error/simulation explainers).
- Layer 2 (on-device LLM): **not implemented.** A usable model (≈0.5–2 GB) cannot be bundled in the APK, MediaPipe LLM Inference needs minSdk 24 (app is 23), and it cannot be verified without a device. A future version could download a model on demand on a 6 GB phone; expect several seconds per answer on a Helio G80.
- Layer 3 (online research): **not implemented**; the app intentionally has no INTERNET permission.
