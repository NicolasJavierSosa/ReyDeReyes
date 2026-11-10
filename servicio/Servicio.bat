@echo off
rem Lo ejecuta el servicio de Windows "ReyDeReyes" (WinSW). No abrir a mano:
rem para iniciar o detener usar "Iniciar Sistema.bat" / "Detener Sistema.bat".
setlocal
cd /d "%~dp0.."

if not exist "config-local.bat" (
  echo Falta config-local.bat en %CD%
  exit /b 1
)
call "config-local.bat"

set "JAR=target\demo-0.0.1-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo No se encontro %JAR%
  exit /b 1
)

if not defined REYDEREYES_JAVA set "REYDEREYES_JAVA=java"

"%REYDEREYES_JAVA%" -Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=America/Argentina/Buenos_Aires -jar "%JAR%"
exit /b %errorlevel%
