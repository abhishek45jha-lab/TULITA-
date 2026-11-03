@echo off
cd /d "%~dp0"
set TULITA_MODE=catalog
set TULITA_DATA_SOURCE=mysql
call mvn javafx:run
pause
