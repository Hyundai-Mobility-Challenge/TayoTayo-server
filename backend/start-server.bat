@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-server.ps1"
if errorlevel 1 (
  echo.
  echo Server startup failed. Check the error message above.
  pause
)
endlocal
