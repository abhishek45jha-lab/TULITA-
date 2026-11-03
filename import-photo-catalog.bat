@echo off
cd /d "%~dp0"
call mvn -q compile exec:java -Dexec.mainClass=tulita.photocatalog.PhotoCatalogImportMain
pause
