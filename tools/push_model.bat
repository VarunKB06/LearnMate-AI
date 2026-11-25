@echo off
rem Usage: push_model.bat model.pte tokenizer.model
if "%~2"=="" (echo Usage: push_model.bat model.pte tokenizer.model & exit /b 1)
set DEST=/sdcard/Android/data/com.varunkb.learnmateai/files/llm/
adb shell mkdir -p %DEST%
adb push "%~1" %DEST%
adb push "%~2" %DEST%
echo Done. In the app: Model screen, Rescan folders, Load model now.
