@echo off
setlocal
rem Antes de detener conviene cerrar la caja (asi se sube todo a la nube).

sc query ReyDeReyes >nul 2>&1
if not errorlevel 1 (
  net session >nul 2>&1
  if errorlevel 1 (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
  )
  net stop ReyDeReyes
  echo El sistema se vuelve a iniciar solo al prender la PC, o con "Iniciar Sistema.bat".
  pause
  exit /b
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
  echo Deteniendo servidor en puerto 8080, PID %%a...
  taskkill /PID %%a /F
  goto :done
)
echo No se encontro un servidor escuchando en el puerto 8080.

:done
endlocal
pause
