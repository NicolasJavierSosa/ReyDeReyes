# =====================================================================
#  Instalador de Rey de Reyes en la PC del local (Windows 10/11).
#  Ejecutar con "instalar\Instalar.bat" (pide permisos de administrador).
#
#  Requisitos previos: Java 21 y PostgreSQL 16 instalados.
#  Se puede ejecutar de nuevo sin problema: no borra datos.
#
#  Hace:
#   1. Verifica Java 21 y PostgreSQL.
#   2. Crea config-local.bat (si no existe) con una clave aleatoria para la base.
#   3. Crea el usuario y la base "reydereyes" en PostgreSQL (si no existen).
#   4. Genera el .jar si falta (necesita JDK e internet la primera vez).
#   5. Instala el servicio de Windows "ReyDeReyes" (arranca solo con la PC).
#   6. Crea el acceso directo "Rey de Reyes" en el escritorio.
# =====================================================================
$ErrorActionPreference = 'Stop'
$Raiz = Split-Path -Parent $PSScriptRoot
Set-Location $Raiz

$Jar = Join-Path $Raiz 'target\demo-0.0.1-SNAPSHOT.jar'
$Config = Join-Path $Raiz 'config-local.bat'
$ConfigEjemplo = Join-Path $Raiz 'config-local.ejemplo.bat'
$WinSW = Join-Path $Raiz 'servicio\ReyDeReyes.exe'
$WinSWUrl = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'
$Url = 'http://localhost:8080'

function Paso($t) { Write-Host ''; Write-Host "==> $t" -ForegroundColor Cyan }
function Ok($t) { Write-Host "    $t" -ForegroundColor Green }
function Aviso($t) { Write-Host "    $t" -ForegroundColor Yellow }
function Falla($t) { Write-Host ''; Write-Host "ERROR: $t" -ForegroundColor Red; Read-Host 'Enter para salir'; exit 1 }

function ClaveAleatoria([int]$largo = 24) {
  $letras = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
  $bytes = New-Object byte[] $largo
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  -join ($bytes | ForEach-Object { $letras[$_ % $letras.Length] })
}

function LeerClave($mensaje) {
  $s = Read-Host $mensaje -AsSecureString
  [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

# Lee las variables "set NOMBRE=valor" de config-local.bat
function LeerConfig {
  $vars = @{}
  foreach ($linea in Get-Content $Config) {
    if ($linea -match '^\s*set\s+"?([A-Z_]+)=([^"]*)"?\s*$') { $vars[$Matches[1]] = $Matches[2] }
  }
  $vars
}

function FijarConfig($nombre, $valor) {
  $texto = Get-Content $Config -Raw
  $patron = '(?m)^(\s*(?:rem\s+)?set\s+"' + $nombre + '=)[^"]*(")'
  if ($texto -match $patron) {
    $texto = [regex]::Replace($texto, $patron, { param($m) ($m.Groups[1].Value -replace '^\s*rem\s+', '') + $valor + $m.Groups[2].Value })
  } else {
    $texto = $texto.TrimEnd() + "`r`nset `"$nombre=$valor`"`r`n"
  }
  [IO.File]::WriteAllText($Config, $texto, [Text.Encoding]::Default)
}

# ---------------------------------------------------------------------
Paso 'Verificando Java 21'
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Falla 'No se encontro Java. Instalar Java 21 (por ejemplo Eclipse Temurin 21) y volver a ejecutar.' }
$version = (& cmd /c "`"$($java.Source)`" -version 2>&1") -join ' '
if ($version -notmatch 'version "(\d+)') { Falla "No se pudo leer la version de Java: $version" }
if ([int]$Matches[1] -lt 21) { Falla "Se necesita Java 21 o superior (instalado: $($Matches[1]))." }
Ok "Java $($Matches[1]) en $($java.Source)"

# ---------------------------------------------------------------------
Paso 'Buscando PostgreSQL'
$psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
  Sort-Object { [int]($_.Directory.Parent.Name -replace '\D', '') } -Descending | Select-Object -First 1
if (-not $psql) { Falla 'No se encontro PostgreSQL en C:\Program Files\PostgreSQL. Instalar PostgreSQL 16 y volver a ejecutar.' }
$pgBin = $psql.Directory.FullName
Ok "PostgreSQL en $pgBin"
$servicioPg = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($servicioPg -and $servicioPg.Status -ne 'Running') { Start-Service $servicioPg.Name; Ok "Servicio $($servicioPg.Name) iniciado" }

# ---------------------------------------------------------------------
Paso 'Configuracion (config-local.bat)'
$configNueva = $false
if (-not (Test-Path $Config)) {
  Copy-Item $ConfigEjemplo $Config
  $configNueva = $true
  FijarConfig 'REYDEREYES_DB_CLAVE' (ClaveAleatoria)
  do {
    $c1 = LeerClave 'Clave para que el duenio ingrese al sistema (minimo 6 caracteres)'
    $c2 = LeerClave 'Repetir la clave'
    if ($c1 -ne $c2) { Aviso 'Las claves no coinciden.' } elseif ($c1.Length -lt 6) { Aviso 'Muy corta.' }
  } while ($c1 -ne $c2 -or $c1.Length -lt 6)
  if ($c1 -match '["%^&|<>]') { Falla 'La clave no puede tener los caracteres " % ^ & | < >' }
  FijarConfig 'REYDEREYES_DUENIO_CLAVE' $c1
  Ok 'Creado config-local.bat (los datos de Supabase se completan despues, ver docs\PUESTA-EN-MARCHA.md)'
} else {
  Ok 'config-local.bat ya existe: se usa tal cual'
}
FijarConfig 'REYDEREYES_JAVA' $java.Source
if ((LeerConfig)['REYDEREYES_PG_DUMP'] -eq $null) { FijarConfig 'REYDEREYES_PG_DUMP' (Join-Path $pgBin 'pg_dump.exe') }
$cfg = LeerConfig

if ($cfg['REYDEREYES_DB_URL'] -notmatch '^jdbc:postgresql://([^:/]+):?(\d*)/([^?]+)') { Falla "REYDEREYES_DB_URL no es valida: $($cfg['REYDEREYES_DB_URL'])" }
$dbHost = $Matches[1]; $dbPuerto = if ($Matches[2]) { $Matches[2] } else { '5432' }; $dbNombre = $Matches[3]
$dbUsuario = $cfg['REYDEREYES_DB_USUARIO']; $dbClave = $cfg['REYDEREYES_DB_CLAVE']
if ($dbClave -eq 'CAMBIAR' -or -not $dbClave) { FijarConfig 'REYDEREYES_DB_CLAVE' (ClaveAleatoria); $cfg = LeerConfig; $dbClave = $cfg['REYDEREYES_DB_CLAVE'] }

# ---------------------------------------------------------------------
Paso "Base de datos '$dbNombre' en PostgreSQL"
function Psql([string]$usuario, [string]$clave, [string]$base, [string]$sql) {
  $env:PGPASSWORD = $clave
  $anterior = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  try { $r = & $psql.FullName -h $dbHost -p $dbPuerto -U $usuario -d $base -tA -v ON_ERROR_STOP=1 -c $sql 2>&1; $ok = $LASTEXITCODE -eq 0 }
  finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue; $ErrorActionPreference = $anterior }
  @{ Ok = $ok; Salida = ($r -join "`n").Trim() }
}

$prueba = Psql $dbUsuario $dbClave $dbNombre 'select 1'
if ($prueba.Ok) {
  Ok "La base ya existe y la clave de config-local.bat funciona"
} else {
  Write-Host '    Hace falta la clave del usuario "postgres" (la que se eligio al instalar PostgreSQL).'
  $pgClave = LeerClave '    Clave de postgres'
  $r = Psql 'postgres' $pgClave 'postgres' 'select 1'
  if (-not $r.Ok) { Falla "No se pudo entrar como postgres: $($r.Salida)" }
  $claveSql = $dbClave.Replace("'", "''")
  $existeRol = (Psql 'postgres' $pgClave 'postgres' "select count(*) from pg_roles where rolname = '$dbUsuario'").Salida
  if ($existeRol -eq '1') {
    $r = Psql 'postgres' $pgClave 'postgres' "alter role `"$dbUsuario`" with login password '$claveSql'"
    Ok "Usuario $dbUsuario existente: se actualizo su clave"
  } else {
    $r = Psql 'postgres' $pgClave 'postgres' "create role `"$dbUsuario`" with login password '$claveSql'"
    Ok "Usuario $dbUsuario creado"
  }
  if (-not $r.Ok) { Falla $r.Salida }
  $existeBase = (Psql 'postgres' $pgClave 'postgres' "select count(*) from pg_database where datname = '$dbNombre'").Salida
  if ($existeBase -ne '1') {
    $r = Psql 'postgres' $pgClave 'postgres' "create database `"$dbNombre`" owner `"$dbUsuario`" encoding 'UTF8' template template0"
    if (-not $r.Ok) { Falla $r.Salida }
    Ok "Base $dbNombre creada (las tablas las crea el sistema al arrancar)"
  } else {
    Ok "Base $dbNombre ya existia"
  }
  if (-not (Psql $dbUsuario $dbClave $dbNombre 'select 1').Ok) { Falla 'El usuario de la base no puede conectarse. Revisar pg_hba.conf.' }
}

# ---------------------------------------------------------------------
Paso 'Programa (.jar)'
if (-not (Test-Path $Jar)) {
  Aviso 'No esta el .jar: se genera ahora (necesita el JDK e internet; tarda varios minutos la primera vez).'
  & cmd /c "`"$Raiz\mvnw.cmd`" -q -DskipTests package"
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Jar)) { Falla 'No se pudo generar el .jar. Copiar target\demo-0.0.1-SNAPSHOT.jar desde la PC de desarrollo y volver a ejecutar.' }
}
Ok $Jar

# ---------------------------------------------------------------------
Paso 'Servicio de Windows "ReyDeReyes"'
if (-not (Test-Path $WinSW)) {
  Write-Host "    Descargando WinSW..."
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  try { Invoke-WebRequest -Uri $WinSWUrl -OutFile $WinSW -UseBasicParsing }
  catch { Falla "No se pudo descargar WinSW ($WinSWUrl). Descargarlo a mano y guardarlo como servicio\ReyDeReyes.exe." }
}
$existente = Get-Service -Name 'ReyDeReyes' -ErrorAction SilentlyContinue
if ($existente) {
  if ($existente.Status -eq 'Running') { & $WinSW stop | Out-Null }
  & $WinSW uninstall | Out-Null
  Start-Sleep -Seconds 2
}
& $WinSW install
if ($LASTEXITCODE -ne 0) { Falla 'No se pudo instalar el servicio.' }
& $WinSW start
Ok 'Servicio instalado (arranca solo al prender la PC)'

Write-Host '    Esperando que el sistema responda (la primera vez crea las tablas)...'
$listo = $false
for ($i = 0; $i -lt 60 -and -not $listo; $i++) {
  Start-Sleep -Seconds 2
  try { Invoke-WebRequest -Uri "$Url/login" -UseBasicParsing -TimeoutSec 3 | Out-Null; $listo = $true } catch { }
}
if ($listo) { Ok "Sistema funcionando en $Url" } else { Aviso "Todavia no responde. Revisar servicio\logs\ReyDeReyes.out.log" }

# ---------------------------------------------------------------------
Paso 'Acceso directo en el escritorio'
$edge = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\msedge.exe' -ErrorAction SilentlyContinue).'(default)'
$chrome = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe' -ErrorAction SilentlyContinue).'(default)'
$navegador = if ($edge -and (Test-Path $edge)) { $edge } elseif ($chrome -and (Test-Path $chrome)) { $chrome } else { $null }
$escritorio = [Environment]::GetFolderPath('CommonDesktopDirectory')
$icono = Join-Path $Raiz 'frontend\public\favicon.ico'
$shell = New-Object -ComObject WScript.Shell
$acceso = $shell.CreateShortcut((Join-Path $escritorio 'Rey de Reyes.lnk'))
if ($navegador) {
  # Modo aplicacion: ventana propia, sin barra de direcciones
  $acceso.TargetPath = $navegador
  $acceso.Arguments = "--app=$Url --start-maximized"
} else {
  $acceso.TargetPath = Join-Path $Raiz 'Iniciar Sistema.bat'
}
$acceso.WorkingDirectory = $Raiz
if (Test-Path $icono) { $acceso.IconLocation = $icono }
$acceso.Description = 'Sistema de ventas Rey de Reyes'
$acceso.Save()
Ok "Creado 'Rey de Reyes' en el escritorio"

Write-Host ''
Write-Host 'Instalacion terminada.' -ForegroundColor Green
if ($configNueva) {
  Write-Host "Usuario: $($cfg['REYDEREYES_DUENIO_USUARIO'])  (clave: la que se ingreso recien)"
  Aviso 'Para activar la copia en la nube completar los datos de Supabase en config-local.bat'
  Aviso 'y reiniciar el servicio con "Detener Sistema.bat" + "Iniciar Sistema.bat".'
}
Read-Host 'Enter para salir'
