@echo off
cd /d "%~dp0"
set TULITA_MODE=mysql
call mvn javafx:run
pause
