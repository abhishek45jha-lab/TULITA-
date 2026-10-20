@echo off
cd /d "%~dp0"
set TULITA_MODE=multistore
set TULITA_DATA_SOURCE=csv
call mvn javafx:run
pause
