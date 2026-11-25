# Installing Llama on the phone (ExecuTorch)

**Verified path (used for the submission):** the pre-exported `Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte` from the `executorch-community` organisation on Hugging Face plus Meta's `tokenizer.model` (gated repo `meta-llama/Llama-3.2-1B-Instruct`, `original/` folder), imported with the in-app buttons, loaded and ran on a Vivo Y20G. Exporting it yourself (below) is the alternative if a ready-made file does not match your runtime version.

The `.pte` model is about 1 GB, so it is not in the APK. You export it once on a computer and install it on the phone.

## 1. Export the model (computer, Linux/macOS/WSL)
1. Accept Meta's licence for Llama 3.2 and download `Llama-3.2-1B-Instruct` (the `original/` folder with `consolidated.00.pth`, `params.json`, `tokenizer.model`).
2. Install ExecuTorch from source or PyPI (`pip install executorch`), following https://docs.pytorch.org/executorch/stable/llm/export-llm.html . Use a recent 1.x release.
3. Export with the XNNPACK backend and 8da4w quantisation. `tools/export_llama_3_2_1b.yaml` is a starting config:
   ```
   python -m executorch.examples.models.llama.export_llama --config tools/export_llama_3_2_1b.yaml
   ```
   (`python -m extension.llm.export.export_llm` on older checkouts.) Config key names change between releases; if one is rejected, compare with the official page above. Keep `max_seq_length` and `max_context_length` at 1024 or higher (the app uses 1024, see `LlmConfig.MAX_SEQ_LEN`).
4. Result: `llama3_2_1b_8da4w.pte` (about 1.1 GB) plus `tokenizer.model`.

The ExecuTorch version used to export should match the runtime in `libs.versions.toml` as closely as possible. If loading fails with a version or schema error, change `executorch` in `gradle/libs.versions.toml` to the release you exported with, or re-export with a matching release.

## 2. Put the files on the phone
Option A, adb (recommended). Open the app, go to Home, then "On-device Llama model setup" once (this creates the folder). Then:
```
tools\push_model.bat llama3_2_1b_8da4w.pte tokenizer.model        (Windows)
./tools/push_model.sh llama3_2_1b_8da4w.pte tokenizer.model       (Linux/macOS)
```
The folder is `/sdcard/Android/data/com.varunkb.learnmateai/files/llm/`.

Option B, in-app import. Copy both files to the phone, open the Model screen, tap "Import model file" and "Import tokenizer file". Needs about 1.5 GB free.

## 3. Check it
Model screen: Rescan folders, Load model now, Run self-test. Note the load time, first-token time and tokens/second; they go into the technical report. The self-test is also saved under Diagnostics.

## 4. Use it
- Plan screen: write a goal; Llama reads it and the plan is built.
- Tutor: lesson questions and "what should I study" are answered by Llama; the source lesson is shown under the answer.
- Plan screen: "Ask Llama for a coaching note".

## Troubleshooting
- Status Error "Could not load": check both files exist, the tokenizer is the Llama 3 `tokenizer.model`, the `.pte` was exported for XNNPACK, and versions match.
- Very slow or app killed: close other apps; try a shorter `max_seq_length`; consider a smaller or more aggressively quantised model.
- Garbage or repeated start of text: set `LlamaPrompts.INCLUDE_BOS = false`.
- Gradle cannot resolve `org.pytorch:executorch-android`: check the version on Maven Central and update `libs.versions.toml`.
