@echo off
cd /d "%~dp0"
set TULITA_MODE=demo
call mvn javafx:run
pause
