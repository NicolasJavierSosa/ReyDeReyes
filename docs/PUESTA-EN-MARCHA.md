# Puesta en marcha: sistema local + Supabase

El sistema corre en la PC del local con su propia base PostgreSQL y vende sin internet.
Cada 5 minutos, al iniciar y (obligatoriamente) al cerrar la caja, sube los cambios a
Supabase y baja lo que el dueño editó del catálogo desde la web.

## 0. Generar el programa (PC de desarrollo)

```bat
mvnw package -DskipTests
```

Genera `target\demo-0.0.1-SNAPSHOT.jar` con el backend y las pantallas Vue adentro.
La primera vez descarga Node.js y las librerías del frontend (necesita internet).
Para compilar solo Java, sin tocar el frontend: `mvnw package -DskipFrontend=true`.

Para trabajar en las pantallas con recarga automática:

```bat
cd frontend
npm run dev        :: http://localhost:5173 (usa la API del sistema en el puerto 8080)
```

## 1. PC del local

1. Instalar **Java 21** (por ejemplo Eclipse Temurin 21) y **PostgreSQL 16** (anotar la
   clave del usuario `postgres`).
2. Copiar la carpeta del sistema (con `target\demo-0.0.1-SNAPSHOT.jar`) a la PC, por
   ejemplo en `C:\ReyDeReyes`.
3. Ejecutar **`instalar\Instalar.bat`** (pide permisos de administrador). El instalador:
   - verifica Java y PostgreSQL;
   - crea `config-local.bat` con una clave aleatoria para la base y pide la clave del dueño;
   - crea el usuario y la base `reydereyes` (pide la clave de `postgres`);
   - instala el servicio de Windows **ReyDeReyes**, que arranca solo al prender la PC y se
     reinicia si se cae;
   - crea el acceso directo **Rey de Reyes** en el escritorio (abre Edge como aplicación).

   Se puede volver a ejecutar sin perder datos (por ejemplo, después de copiar un .jar nuevo).
4. Abrir **Rey de Reyes**, ingresar con el usuario `duenio` y la clave elegida, abrir la caja
   y vender. La clave se cambia en Configuración.

| Archivo | Para qué |
| --- | --- |
| `Iniciar Sistema.bat` | Inicia el servicio si estaba detenido y abre la pantalla |
| `Detener Sistema.bat` | Detiene el servicio (conviene cerrar la caja antes) |
| `instalar\Desinstalar.bat` | Quita el servicio y el acceso directo; no borra la base |
| `servicio\logs\` | Registros del sistema para revisar errores |

Al cambiar `config-local.bat` (por ejemplo, para activar Supabase), detener e iniciar el sistema.
El servidor escucha solo en `127.0.0.1:8080`: no es accesible desde otras PCs de la red.

Instalación manual (sin el instalador): crear la base con
`create user reydereyes with password '...'; create database reydereyes owner reydereyes;`,
copiar `config-local.ejemplo.bat` como `config-local.bat`, completarlo y usar `Iniciar Sistema.bat`.

## 2. Supabase (una sola vez)

1. Crear el proyecto (región São Paulo).
2. **Authentication → Providers → Email**: desactivar "Allow new users to sign up" y crear
   a mano el usuario del dueño para la web.
3. Aplicar las migraciones (desde cualquier PC con el .jar):

   ```bat
   set SUPABASE_ADMIN_URL=jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require
   set SUPABASE_ADMIN_USUARIO=postgres.REFERENCIA_DEL_PROYECTO
   set SUPABASE_ADMIN_CLAVE=clave_del_proyecto
   java -jar target\demo-0.0.1-SNAPSHOT.jar --migrar-nube
   ```

   Crea las mismas tablas que el local más las funciones de sincronización, el rol
   `sync_local` y las reglas de acceso de la web.
4. En el SQL Editor de Supabase, darle clave al rol que usa la PC del local:

   ```sql
   alter role sync_local with login password 'OTRA_CLAVE_LARGA';
   ```

5. En `config-local.bat` de la PC del local:
   - `SUPABASE_SYNC_HABILITADA=true`
   - `SUPABASE_DB_URL` = URL del pooler en **modo sesión** (puerto 5432)
   - `SUPABASE_SYNC_USUARIO=sync_local.REFERENCIA_DEL_PROYECTO`
   - `SUPABASE_SYNC_CLAVE` = la clave del paso anterior

El rol `sync_local` solo puede ejecutar `sync_push`, `sync_pull`, `sync_exportar` y
`sync_cursor_actual`: aunque alguien obtenga esa clave, no puede leer ni borrar tablas.

## 3. Web del dueño en Vercel

Es el mismo frontend Vue compilado en modo "nube": habla directo con Supabase (Auth + RLS),
sin servidor propio.

1. En Supabase, **Project Settings → API**: copiar la *Project URL* y la *anon public key*.
2. En Vercel, **Add New → Project**, importar el repositorio y configurar:
   - **Root Directory**: `frontend` (el resto lo toma de `frontend/vercel.json`).
   - **Environment Variables**: `VITE_MODO=nube`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`.
3. Deploy. El dueño entra con el usuario creado en Supabase Auth (paso 2.2).
4. En Supabase, **Authentication → URL Configuration**: poner la dirección de Vercel como
   *Site URL*.

La anon key es pública por diseño: lo que protege los datos son las reglas RLS de abajo.
Probar en la PC de desarrollo: copiar `frontend/env.nube.ejemplo` como `frontend/.env.nube`,
completarlo y ejecutar `npm run dev:nube`.

Qué ve y qué edita la web (reglas RLS para usuarios autenticados):

| Datos | Permiso |
| --- | --- |
| Productos, combos, categorías, unidades, tipos, precios | Leer, crear y editar |
| Ventas, detalles, pagos, cajas, movimientos de tesorería, métodos de pago | Solo leer (las ventas de la web se hacen con las funciones de contingencia) |
| Movimientos de stock y vista `v_stock` | Solo leer |
| Usuarios, configuración, tablas de sincronización internas | Sin acceso |

Los cambios de catálogo llegan a la PC del local en la próxima sincronización. Si el local y
la web editan el mismo producto a la vez, gana el último y el otro queda en
la pantalla de **Conflictos** (ícono en la barra superior) para revisarlo.

### Vender desde la web (contingencia)

Si la PC del local no está disponible, el dueño puede vender desde la web (menú **Venta**):

- La web tiene **su propia caja**, independiente de la del local: se abre con su efectivo
  inicial y se cierra con su propio arqueo (botón **Cerrar caja** en la pantalla de venta).
- Cobrar, controlar stock, aplicar combos y registrar el pago se hace en una sola operación
  en Supabase: o queda todo guardado o no queda nada.
- Los tickets de la web empiezan en **900001**, para no pisarse con los del local.
- Las ventas y la caja de la web **bajan solas a la PC del local** en la próxima
  sincronización y descuentan el stock allá. Desde el local no se pueden anular.
- Cada lado controla el stock con lo que ve. Si el local y la web venden lo mismo a la vez,
  el stock puede quedar en negativo y hay que ajustarlo a mano.
- Los productos "por monto" (fiambrería, carnicería, verdulería) se venden desde la web
  solo si ya existen; los nuevos se crean en el local.

Al actualizar a esta versión hay que volver a ejecutar `--migrar-nube` (aplica las
funciones nuevas en Supabase) y publicar de nuevo en Vercel.

Vercel Hobby es para uso no comercial según sus condiciones; si lo objetan, el mismo
`frontend` se publica igual en Cloudflare Pages o Netlify.

## 4. Recuperar el sistema si la PC se rompe

1. Instalar Java y PostgreSQL, copiar la carpeta del sistema y el `config-local.bat`
   anterior (o completar de nuevo los datos de Supabase).
2. Ejecutar `instalar\Instalar.bat` (crea la base vacía).
3. Ingresar al sistema.
4. En **Configuración**, usar **Restaurar PC nueva desde la nube** (una sola vez).
   Baja todo desde Supabase y la numeración de tickets continúa. Solo funciona con la base
   local vacía.

Si había cambios que no llegaron a subirse, están en la copia diaria (`pg_dump`) si estaba
configurada `REYDEREYES_CARPETA_COPIAS`.

## 5. API del sistema local

| Endpoint | Para qué |
| --- | --- |
| `GET /api/sync/estado` | Indicador: VERDE, AMARILLO (pendientes), ROJO (más de 1 hora sin subir), DESACTIVADA |
| `POST /api/sync/ahora` | Botón "Sincronizar ahora" |
| `GET /api/sync/conflictos`, `PATCH /api/sync/conflictos/{id}/revisado` | Pantalla de conflictos |
| `POST /api/sync/restaurar` | Restaurar una PC nueva |
| `GET/POST/PUT /api/metodos-pago` | Lista de métodos de pago del dueño |
| `POST /api/productos/{id}/ajuste-stock` | Corregir stock (queda registrado como ajuste) |
| `GET /api/productos/{id}/movimientos` | Historial de stock de un producto |
| `GET /api/cuenta`, `POST /api/cuenta/clave` | Datos y cambio de clave del dueño |
| `POST /api/auth/login`, `POST /api/auth/logout` | Ingreso y salida (formulario `username`/`password`) |

## 6. Pruebas automáticas

Necesitan un PostgreSQL de prueba con dos bases vacías:

```bat
set REYDEREYES_TEST_DB_URL=jdbc:postgresql://localhost:5432/reydereyes_test
set REYDEREYES_TEST_NUBE_URL=jdbc:postgresql://localhost:5432/reydereyes_test_nube
set REYDEREYES_TEST_DB_USUARIO=postgres
set REYDEREYES_TEST_DB_CLAVE=...
mvnw test
```

Sin esas variables, `mvnw test` solo compila y omite las pruebas de integración.
