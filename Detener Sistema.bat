@echo off
setlocal

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
  echo Deteniendo servidor en puerto 8080, PID %%a...
  taskkill /PID %%a /F
  goto :done
)

echo No se encontro un servidor escuchando en el puerto 8080.

:done
endlocal
pause
