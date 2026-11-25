#!/usr/bin/env bash
# Usage: ./push_model.sh model.pte tokenizer.model
set -e
[ $# -ge 2 ] || { echo "Usage: $0 model.pte tokenizer.model"; exit 1; }
DEST=/sdcard/Android/data/com.varunkb.learnmateai/files/llm/
adb shell mkdir -p "$DEST"
adb push "$1" "$DEST"
adb push "$2" "$DEST"
echo "Done. In the app: Model screen, Rescan folders, Load model now."
