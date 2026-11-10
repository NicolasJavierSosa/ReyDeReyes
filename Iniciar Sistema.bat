@echo off
setlocal
cd /d "%~dp0"
set "URL=http://localhost:8080"

rem --- Instalado como servicio (lo normal, ver instalar\Instalar.bat) ---
sc query ReyDeReyes >nul 2>&1
if not errorlevel 1 (
  sc query ReyDeReyes | find "RUNNING" >nul
  if errorlevel 1 (
    echo Iniciando el servicio ReyDeReyes...
    net session >nul 2>&1
    if errorlevel 1 (
      powershell -NoProfile -Command "Start-Process -FilePath sc.exe -ArgumentList 'start','ReyDeReyes' -Verb RunAs -WindowStyle Hidden -Wait"
    ) else (
      net start ReyDeReyes
    )
  )
  goto :abrir
)

rem --- Sin servicio: arranca a mano (pruebas o PC de desarrollo) ---
if exist "config-local.bat" (
  call "config-local.bat"
) else (
  echo Falta config-local.bat. Ejecutar instalar\Instalar.bat o copiar config-local.ejemplo.bat como config-local.bat.
  pause
  exit /b 1
)
set "JAR=target\demo-0.0.1-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo No se encontro %JAR%. Generando el archivo .jar...
  call ".\mvnw.cmd" -DskipTests package
  if errorlevel 1 (
    echo.
    echo No se pudo generar el .jar. Revisar el error anterior.
    pause
    exit /b 1
  )
)
if not defined REYDEREYES_JAVA set "REYDEREYES_JAVA=javaw"
echo Iniciando el sistema...
start "ReyDeReyes Sistema" "%REYDEREYES_JAVA:java.exe=javaw.exe%" -Duser.timezone=America/Argentina/Buenos_Aires -jar "%JAR%"

:abrir
echo Esperando al sistema...
powershell -NoProfile -Command "for($i=0;$i -lt 45;$i++){try{Invoke-WebRequest '%URL%/login' -UseBasicParsing -TimeoutSec 2 | Out-Null; exit 0}catch{Start-Sleep 2}}; exit 1"
if errorlevel 1 echo El sistema tarda en responder. Si no abre, revisar servicio\logs.

rem Abre en Edge como aplicacion (ventana propia, sin barra de direcciones)
start "" msedge --app=%URL%
endlocal
