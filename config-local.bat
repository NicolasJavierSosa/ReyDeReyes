@echo off
rem =====================================================================
rem  Copiar este archivo como config-local.bat y completar los datos.
rem  config-local.bat NO se sube a Git (tiene claves).
rem =====================================================================

rem --- Base de datos local (PostgreSQL instalado en esta PC) ---
set "REYDEREYES_DB_URL=jdbc:postgresql://localhost:5432/reydereyes"
set "REYDEREYES_DB_USUARIO=reydereyes"
set "REYDEREYES_DB_CLAVE=LitR5TW5upKwtVrbfBcjoQ2o"

rem --- Usuario del duenio (solo se usa la primera vez que arranca) ---
set "REYDEREYES_DUENIO_USUARIO=duenio"
set "REYDEREYES_DUENIO_CLAVE=adrian"
set "REYDEREYES_DUENIO_NOMBRE=Duenio"

rem --- Sincronizacion con Supabase (rol restringido sync_local, pooler en modo sesion) ---
set "SUPABASE_SYNC_HABILITADA=true"
set "SUPABASE_DB_URL=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
set "SUPABASE_SYNC_USUARIO=sync_local.puolfyfupkhzsvkcixdo"
set "SUPABASE_SYNC_CLAVE=n8a9x0liKlplfZHL35USsNzAmq7h"

rem --- Copias de seguridad con pg_dump (opcional) ---
rem set "REYDEREYES_CARPETA_COPIAS=D:\CopiasReyDeReyes"
rem set "REYDEREYES_DB_URL_LIBPQ=postgresql://localhost:5432/reydereyes"
set "REYDEREYES_PG_DUMP=C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"
rem set "SUPABASE_BACKUP_URL=postgresql://postgres.REFERENCIA@aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
rem set "SUPABASE_BACKUP_CLAVE=CAMBIAR"
set "REYDEREYES_JAVA=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"

rem --- SOLO para preparar Supabase (--migrar-nube). BORRAR estas 3 lineas despues ---
set "SUPABASE_ADMIN_URL=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
set "SUPABASE_ADMIN_USUARIO=postgres.puolfyfupkhzsvkcixdo"
set "SUPABASE_ADMIN_CLAVE=15600269Nicolas!"
