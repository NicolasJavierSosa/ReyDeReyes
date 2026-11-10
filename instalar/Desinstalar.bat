@echo off
rem Quita el servicio de Windows y el acceso directo. NO borra la base de datos ni config-local.bat.
net session >nul 2>&1
if errorlevel 1 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
cd /d "%~dp0.."
if exist "servicio\ReyDeReyes.exe" (
  "servicio\ReyDeReyes.exe" stop
  "servicio\ReyDeReyes.exe" uninstall
)
powershell -NoProfile -Command "Remove-Item -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'Rey de Reyes.lnk')"
echo.
echo Servicio y acceso directo quitados. La base de datos sigue intacta.
pause
