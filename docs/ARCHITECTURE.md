# Architecture

```
 Compose UI (ui/*) ──► MainViewModel ──► LearnRepository ──► Room (SQLite, on disk)
        ▲                    │                 ▲
        │                    ▼                 │
        └──────── StateFlow ◄─ domain (pure Kotlin) ◄── assets/content/*.json (read-only seed)
```

## Layers

**domain/** has no Android imports, so it runs in plain JVM unit tests.
`Bkt` (mastery update), `QuizSelector` / `QuizScoring` / `Recommender`, `TextAnalyzer` + `Bm25Index` +
`RetrievalTutorEngine`, `ContentParser` + `MiniJson` (dependency-free JSON), `LegacyMigration`, `Stats`.

**data/** holds Room.
Entities: `profile`, `subjects`, `questions`, `attempts`, `attempt_answers`, `mastery`, `chat_messages`, `benchmark_runs`.
Foreign keys cascade (subject → questions/attempts, attempt → answers) and every foreign-key column is indexed.
DAOs expose `Flow` for UI state and `suspend` functions for writes. Multi-table writes are `@Transaction` methods
(`commitQuiz` saves attempt + answers + mastery atomically; `replaceSubject` swaps a question bank without touching progress).

**perf/** `BenchmarkRunner`: measures the app's own workloads on the device. Writes use a temporary DB file.

**ui/** stateless composables; `MainViewModel` owns navigation state, the running quiz, results and benchmark state, so
rotation does not lose a quiz in progress.

## Offline-first guarantees

- No INTERNET permission in the manifest, so the app cannot reach the network.
- Content ships in `assets/`, is parsed at start-up and seeded into Room once per `CONTENT_VERSION`.
- All user data (profile, attempts, answers with timings, mastery, chat, benchmarks) is stored in Room.
- Start-up work (parse, seed, import, index build) runs off the main thread; the UI shows a loading state until done.

## Database and migrations

Version 1 is the first Room schema, exported to `app/schemas/` on build. `LearnMateDatabase.MIGRATIONS` is where future
`Migration` objects go; destructive fallback is deliberately not enabled. The migration that exists today is the
data migration from the old SharedPreferences store (`LegacyPrefsImporter`): runs once, leaves the old file untouched.
Because old data held only aggregated per-topic counts, imported mastery is a Laplace-smoothed estimate,
`(correct+1)/(total+2)`, and imported attempts are marked `source = "legacy"`.

## Key decisions

- **Own JSON parser**: the same content-loading code runs in JVM tests (Android's `org.json` does not).
- **Manual DI container** (`AppContainer`): enough for this size; no framework dependency risk.
- **Retrieval tutor instead of an LLM**: see the AI design document.
