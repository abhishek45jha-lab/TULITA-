@echo off
REM Build a self-contained Tulita app for Windows. The output folder runs without Maven or a JDK.
setlocal enabledelayedexpansion
cd /d "%~dp0"
set APP_NAME=Tulita
set DEST=dist
set INPUT=target\app-input

where jpackage >nul 2>nul
if errorlevel 1 (
  echo jpackage was not found. Install a full 64-bit JDK 21 from https://adoptium.net and reopen the terminal.
  pause
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found on PATH.
  pause
  exit /b 1
)

echo 1/4  Building the application jar...
call mvn -q -DskipTests package
if errorlevel 1 goto :failed

echo 2/4  Collecting runtime libraries...
if exist "%INPUT%" rmdir /s /q "%INPUT%"
mkdir "%INPUT%"
call mvn -q dependency:copy-dependencies -DoutputDirectory="%INPUT%" -DincludeScope=runtime
if errorlevel 1 goto :failed
for %%f in (target\tulita-*.jar) do copy "%%f" "%INPUT%\" >nul
for %%f in (target\*.jar) do if not exist "%INPUT%\%%~nxf" copy "%%f" "%INPUT%\" >nul

set MAINJAR=
for %%f in ("%INPUT%\tulita*.jar") do if not defined MAINJAR set MAINJAR=%%~nxf

echo 3/4  Packaging a self-contained app image...
if exist "%DEST%\%APP_NAME%" rmdir /s /q "%DEST%\%APP_NAME%"
call jpackage --type app-image --name %APP_NAME% --app-version 3.0.0 --input "%INPUT%" --main-jar "%MAINJAR%" --main-class tulita.Launcher --dest "%DEST%" --java-options "-Xmx1024m" --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto :failed

echo 4/4  Copying the curated catalog next to the launcher...
mkdir "%DEST%\%APP_NAME%\datasets"
xcopy /e /i /q "datasets\promap" "%DEST%\%APP_NAME%\datasets\promap" >nul

if /i "%TULITA_INCLUDE_MODEL%"=="1" if exist "%USERPROFILE%\.tulita\clip-vision-int8.onnx" (
  mkdir "%DEST%\%APP_NAME%\model" 2>nul
  copy "%USERPROFILE%\.tulita\clip-vision-int8.onnx" "%DEST%\%APP_NAME%\model\clip-vision-int8.onnx" >nul
  echo Model copied: photo search works fully offline.
) else (
  echo Model NOT copied ^(keeps the build under 300 MB^).
  echo First launch offers the verified 89 MB download, or set TULITA_INCLUDE_MODEL=1 and re-run.
)

echo.
echo Done: %DEST%\%APP_NAME%\Tulita.exe
echo To create a normal Windows installer as well, run:
echo   jpackage --type msi --name Tulita --app-version 3.0.0 --input "%INPUT%" --main-jar "%MAINJAR%" --main-class tulita.Launcher --dest "%DEST%" --win-shortcut --win-menu
echo (an MSI needs the free WiX Toolset on PATH)
echo.
pause
exit /b 0

:failed
echo Packaging failed. Check the messages above; the source project is unchanged.
pause
exit /b 1
