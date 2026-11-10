# Implementation status (honest)

Labels: **Verified (JVM)** = compiled with Kotlin 2.0.21 and covered by passing JUnit tests in the build sandbox. **Written, not compiled** = Android code (Compose/Room/ViewModel) that could not be built here (no Android SDK or Maven access). **Not implemented**.

| Feature | Status | Notes |
|---|---|---|
| Lesson catalogue, subject pages, lesson reader | Written, not compiled | Pure logic (Catalog, progress, prerequisites, recommendations) Verified (JVM) |
| Persisted lesson progress and bookmarks | Written, not compiled | DAO + Room v2 tables; migration SQL checked with Python sqlite3 only |
| 15 subjects, 118 lessons, 411 questions | Verified (JVM) | Structure validated; 3 subjects (Python, DSA, DBMS) still use the shorter V1 lesson format; content not reviewed by a subject expert |
| Prerequisites / learning paths | Verified (JVM) logic, UI not compiled | |
| Verilog parser, checker, event simulator (0/1/x, ≤64 bit) | Verified (JVM) | Subset documented below; 4-state, NBA region, time limits |
| Editor: highlighting, line numbers, indent, format | Format Verified (JVM); editor UI Written, not compiled | |
| Multi-file projects, templates, save/rename/delete | Written, not compiled | |
| Console, errors, warnings, clear | Written, not compiled | Diagnostics produced by Verified code |
| Waveform viewer + VCD share | Written, not compiled | Wave data produced by Verified simulator |
| Untrusted-code limits (time, steps, output, wall clock, cancel) | Verified (JVM) | |
| Offline tutor: BM25, sources, "I don't know" | Verified (JVM) | Top-1 retrieval 97.4% on 38 author-written queries |
| Tutor skills: follow-ups, level, compare, practice, Verilog line-by-line, error/simulation explanation, next-study | Verified (JVM) rule-based | Not an LLM; heuristic text rules |
| Tutor UI with lesson links | Written, not compiled | |
| Quiz-mistake explanation by tutor | Partial | Tutor can explain a topic and give practice; no per-question "why was I wrong" hook |
| Layer 2 on-device LLM | **Not implemented** | See docs/AI_DESIGN_AND_FEASIBILITY.md: needs a ~0.5–2 GB model download, cannot be verified without device; no model in APK |
| Layer 3 online research (Wikipedia etc.) | **Not implemented** | Kept offline by design; no INTERNET permission |
| BKT mastery, adaptive quiz | Verified (JVM) | |
| Dashboard, Progress, Profile | Written, not compiled | Carried over from V1 which ran on the phone |
| Room migration that preserves data | Written, not compiled | `MIGRATION_1_2` additive only |
| Android build, instrumented tests, Vivo Y20G run | **Not done** | |

## Verilog subset
Supported: modules, ports, wire/reg/logic/integer, parameters, assign, always @(...)/@(*), always_ff/always_comb, initial, if/else, case/casez/casex, for, begin/end, delays, instances (named/ordered, parameter override), memories, bit/part select, concat/replication, $display/$write/$monitor/$finish/$time/$dumpfile/$dumpvars. Unsupported (clear error): function/task/generate/signed/inout/real etc.
