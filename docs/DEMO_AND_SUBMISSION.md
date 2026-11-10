# Demo script and submission checklist

Deadline: **23 November 2026**. Check the official ARM Bharat Challenge brief for required artefacts; this checklist is general.

## 3–4 minute demo script (physical Vivo Y20G, airplane mode ON)

1. **Offline proof (10 s)**: show airplane mode on; open the app. Mention: no INTERNET permission.
2. **Onboarding (20 s)**: Let's Begin → name, goal, choose *DBMS and SQL* → Save.
3. **Adaptive quiz (60 s)**: Start a practice quiz → answer 10 (get a few topics wrong on purpose) → result: score, topic scores, *updated mastery*, mistake explanations.
4. **It adapts (30 s)**: dashboard shows the recommendation for the weakest topic. Start another quiz and say it now favours that topic (BKT mastery).
5. **Subjects (15 s)**: Change subject → Python; different questions, separate progress.
6. **Offline tutor (45 s)**: ask "Explain binary search" (answer + source lesson + match %); "Show my progress"; "What is the capital of France?" (honestly says it doesn't know); "Are you an LLM?".
7. **On-device performance (45 s)**: Device diagnostics → quick benchmark → show p95 latencies, memory, temperature.
8. **Close (10 s)**: About the AI screen: BKT + BM25, not an LLM.

## Submission checklist
- [ ] Open in Android Studio, sync, build debug. Fix anything that fails (see KNOWN_LIMITATIONS.md) and tell me the error text if you need help.
- [ ] `testDebugUnitTest` passes (51 tests).
- [ ] `connectedDebugAndroidTest` on the Vivo; note any failures.
- [ ] Manual checklist in TESTING_AND_BENCHMARKS.md, including the upgrade test.
- [ ] Run the benchmarks on the Vivo and fill in the results table (use a release build).
- [ ] `assembleRelease` → keep `app-release.apk`; install and smoke-test it.
- [ ] Screenshots: welcome, dashboard, quiz, result with mastery, assistant, diagnostics.
- [ ] Record a 3–4 minute screen recording of the demo script.
- [ ] Commit and push to GitHub (`VarunKB06/LearnMate-AI`), include the `docs/` folder and `app/schemas/` (generated on first build).
- [ ] Use the honest AI wording from AI_DESIGN_AND_FEASIBILITY.md in the write-up. Do not describe the tutor as an LLM or generative AI.
- [ ] Optional if time remains: the LLM/LiteRT stretch path in the AI design document; measure on the phone first.

## Suggested git commit
```
git add -A
git commit -m "Room, multi-subject content, BKT adaptive quizzes, offline BM25 tutor, diagnostics, tests, docs"
git push
```
