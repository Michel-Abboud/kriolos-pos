@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-windows-installer.ps1" %*
exit /b %ERRORLEVEL%
