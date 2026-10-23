# sync-service

Microservicio de **replicación asíncrona a NoSQL** (Fase 2). Copia datos del
sistema de agua (MySQL) hacia MongoDB sin modificar el monolito.

## Qué hace

- `POST /api/sync/jobs` inicia una sincronización en segundo plano y devuelve un `jobId` (HTTP 202).
- `GET /api/sync/jobs/{id}` devuelve el estado y el progreso (0 a 100) del trabajo.
- Si ya hay una sincronización en proceso, el `POST` responde HTTP 409.

## Cómo detecta diferencias

Cada documento guarda un hash SHA-256 de su contenido (`_hash`). En cada
corrida se compara con el de Mongo:

| Situación | Acción |
|---|---|
| No existe en Mongo | Insertar |
| Existe y el hash cambió | Actualizar |
| Existe y el hash es igual | Omitir |
| Ya no existe en MySQL | **No se borra** en Mongo |

El monolito no se toca: el servicio usa un usuario MySQL con permiso solo de `SELECT`.

## Colecciones en Mongo (base `gota_replica`)

- `clientes`: copia de la tabla Clientes.
- `pagos`: cada pago con su `lectura`, `contador` y `cliente` incrustados.
- `sync_jobs`: historial de trabajos (estado, progreso, contadores).

## Requisitos

Java 25 o superior, Maven (incluido como `mvnw`), Docker, y el MySQL del monolito corriendo.

## Cómo correrlo

1. Crear el usuario de solo lectura en MySQL:

```sql
CREATE USER 'sync_ro'@'localhost' IDENTIFIED BY 'SyncRo2026';
GRANT SELECT ON gota_db.* TO 'sync_ro'@'localhost';
FLUSH PRIVILEGES;
```

2. Levantar MongoDB:

```
docker compose up -d
```

3. Arrancar el servicio:

```
./mvnw spring-boot:run
```

4. Abrir la documentación OpenAPI: http://localhost:8081/swagger-ui.html

## Configuración (variables de entorno)

| Variable | Por defecto |
|---|---|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/gota_db` |
| `MYSQL_USER` | `sync_ro` |
| `MYSQL_PASSWORD` | `SyncRo2026` (solo desarrollo local) |
| `MONGO_URI` | `mongodb://localhost:27017/gota_replica` |
| `SERVER_PORT` | `8081` |
| `SYNC_DEMORA_MS` | `0` (usar `1000` para ver el progreso en una demo) |

## Respuesta de ejemplo

```json
{
  "estado": "COMPLETADO",
  "progreso": 100,
  "total": 9,
  "insertados": 0,
  "actualizados": 1,
  "sinCambios": 8,
  "error": null
}
```