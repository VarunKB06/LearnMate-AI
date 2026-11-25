# Testing and benchmarks

## 1. What was actually run (build sandbox, JVM only)

The domain package was compiled with the Kotlin compiler (2.0.21, bundled with Gradle) and the tests run with JUnit 4:

**51 tests, 0 failures.** Coverage: BKT maths (hand-computed value), bounds and convergence; quiz selection (count, no duplicates,
choice shuffle keeps the right answer, weak-topic bias, recency penalty, per-topic cap); scoring; recommender; MiniJson parser;
content validity of all 4 packs (≥30 questions each, unique ids, 4 distinct choices, valid answers, lesson for every topic, original
12 C questions preserved); legacy-data migration; tutor behaviour (lesson answers, "don't know", greeting, progress, study plan,
honesty about not being an LLM, subject tie-breaking); retrieval gold-set accuracy (97.4%).

Engine speed on the **build machine** (2 vCPU server, JVM, NOT the phone), for orientation only:

| Operation | mean | p95 |
|---|---|---|
| Tutor index build (118 lessons) | 1.5 ms | 1.7 ms |
| Tutor answer | 0.013 ms | 0.019 ms |
| Adaptive quiz selection (10 of 30) | 0.026 ms | 0.037 ms |

## 2. What was NOT run here (needs Android Studio and your phone)

The Android layer and instrumented tests were not built in the original sandbox. Since then the app (V3 plus the V4 Llama, goal and plan features) has been built in Android Studio and run on a physical Vivo Y20G by the author; the Llama self-test results are in docs/TECHNICAL_REPORT.md. The instrumented tests (`connectedDebugAndroidTest`) have not been reported as run.

## 3. Commands

```
gradlew.bat testDebugUnitTest            # JVM tests (51)
gradlew.bat connectedDebugAndroidTest    # Room DAO tests + Compose UI flow on the connected Vivo
gradlew.bat assembleRelease              # app/build/outputs/apk/release/app-release.apk
```

## 4. On-device benchmarks on the Vivo Y20G

### In-app (Dashboard → Device diagnostics)
- **Quick benchmark**: index build, Room read, quiz selection, tutor answers, Room write; p50/p95/max; PSS and heap before/after.
- **Sustained 2 min / 10 min**: loops tutor + selection + Room read, reports ops/s for the first vs last 10% (ratio well below 1 → throttling),
  battery % and temperature before/after. Unplug the charger; close other apps. Use "Copy report" and paste into the table below.

### adb (laptop, phone connected)
```
adb shell am force-stop com.varunkb.learnmateai
adb shell am start -W -n com.varunkb.learnmateai/.MainActivity        # TotalTime = cold start (ms)
adb shell dumpsys meminfo com.varunkb.learnmateai                      # TOTAL PSS
adb shell dumpsys batterystats --reset                                  # then use the app 10 min unplugged
adb shell dumpsys batterystats com.varunkb.learnmateai                  # estimated power use
adb shell dumpsys gfxinfo com.varunkb.learnmateai                       # janky frame %
```
Do the release build for final numbers (debug builds are slower).

### Results to fill in (not measured yet)

| Metric | Vivo Y20G result |
|---|---|
| Cold start (adb `TotalTime`) | _ |
| Start-up inside app (parse / seed / index) | _ |
| Tutor answer p50 / p95 | _ |
| Quiz selection p50 / p95 | _ |
| Room read / write p95 | _ |
| PSS after 5 min use | _ |
| Sustained 10 min: throughput ratio, battery %, temp °C | _ |
| Release APK size | _ |

## 5. Manual test checklist
- Fresh install: Welcome → profile → dashboard.
- Switch to each of the 4 subjects; dashboard, progress and assistant change subject.
- Complete a quiz in each subject; result shows topic scores, updated mastery and mistake explanations.
- Rotate during a quiz: question and answers are kept.
- Back button: quiz → dashboard; other screens → dashboard.
- Airplane mode: everything still works.
- Upgrade test: install the previous APK, create a profile and a quiz, then install this build over it; profile and history appear and Progress shows "(imported)" attempts.
- Assistant: "Explain pointers", "What is a primary key", "Show my progress", "Which topic should I revise", "capital of France" (must say it doesn't know), "Are you an LLM?" (must say no).

## V4 device test checklist (do in this order)
1. Gradle sync and `gradlew.bat testDebugUnitTest` pass; fix any compile errors in the new files first.
2. Install on the phone over the old version; confirm old data (profile, mastery, chats) is still there.
3. Without a model: Plan screen builds a plan from a typed goal (source "keyword rules"); tutor works as before; About says no model installed.
4. Install the model (docs/LLAMA_EXECUTORCH_SETUP.md); Model screen: Rescan, Load, self-test. Record numbers.
5. Plan screen with model: goal source becomes "Llama on-device"; try 5 different goals, note wrong subject picks.
6. Quiz, then check the plan changed (notes + revision items).
7. Tutor: 5 lesson questions + "what should I study this week?"; press Stop once; confirm the offline answer appears.
8. Switch Llama off on the Model screen; confirm answers revert to rule-based.
9. Leave the app in the background and return; confirm no crash; unload model and reload.
