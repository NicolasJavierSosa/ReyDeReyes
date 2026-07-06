@echo off
setlocal
cd /d "%~dp0"

set "JAR=target\demo-0.0.1-SNAPSHOT.jar"
set "URL=http://localhost:8080"

if not exist "%JAR%" (
  echo No se encontro %JAR%.
  echo Generando el archivo .jar...
  call ".\mvnw.cmd" -DskipTests package
  if errorlevel 1 (
    echo.
    echo No se pudo generar el .jar. Revisar el error anterior.
    pause
    exit /b 1
  )
)

echo Iniciando el sistema...
start "ReyDeReyes Sistema" javaw -jar "%JAR%"

echo Abriendo navegador...
timeout /t 8 /nobreak >nul
start "" "%URL%"

endlocal
