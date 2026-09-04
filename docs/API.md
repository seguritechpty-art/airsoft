# 🔌 API del Backend

Base URL: `http://localhost:3000`

## REST

### `GET /health`
Estado del servidor.

```json
{ "status": "ok", "uptime": 1234.5, "users": 12 }
```

### `POST /api/squad/create`
Crear partida nueva.

```json
// Request
{ "nick": "Alpha", "name": "Partida del sábado" }

// Response 201
{
  "squadCode": "XC3WAX",
  "squadName": "Partida del sábado",
  "userId": "978f8700fcb06c68",
  "token": "8b5a951dfde414ac...",
  "user": { "id": "...", "nick": "Alpha", "color": "hsl(126, 70%, 50%)", "online": true }
}
```

### `POST /api/squad/join`
Unirse a partida existente.

```json
// Request
{ "nick": "Bravo", "squadCode": "XC3WAX" }

// Response 200 (mismo formato que create, el nick se reutiliza si existe)
```

### `GET /api/squad/:code/state`
Estado completo de la sala (users + objectives + areas).

```json
{
  "squad": { "code": "XC3WAX", "name": "Partida del sábado" },
  "users": [ { "id": "...", "nick": "Alpha", "online": true, "lat": 8.98, "lng": -79.52, "speed": 1.2, "updated_at": 1788542773562 } ],
  "objectives": [ { "id": "...", "name": "OBJ Alpha", "lat": 8.99, "lng": -79.52, "completed": 0, "radius": 100 } ],
  "areas": []
}
```

## WebSocket (Socket.IO)

Conexión: `io("https://tu-servidor")` con transports `["websocket","polling"]`.

### Eventos entrantes (cliente → servidor)

| Evento | Payload | Descripción |
|---|---|---|
| `squad:auth` | `{ nick, squadCode }` | Autentica y une a la sala. Ack: `{ ok }` + estado inicial |
| `location:update` | `{ lat, lng, heading?, speed?, accuracy? }` | Actualiza tu posición (broadcast a la sala) |
| `chat:message` | `{ text }` | Envía mensaje al chat del escuadrón |
| `objective:add` | `{ name, description?, lat, lng, color?, radius? }` | Crea objetivo. Ack: `{ ok, objective }` |
| `objective:complete` | `{ id, completed }` | Marca/desmarca objetivo completado |
| `area:add` | `{ name, color?, opacity?, coordinates: [[lat,lng],...], type }` | Crea área (circle/polygon) |

### Eventos salientes (servidor → cliente)

| Evento | Payload | Descripción |
|---|---|---|
| `squad:state` | `{ users, objectives, areas }` | Estado completo (al conectarse) |
| `location:update` | `{ id, nick, color, lat, lng, heading, speed, accuracy, updated_at }` | Posición actualizada de un miembro |
| `chat:message` | `{ id, nick, text, created_at }` | Nuevo mensaje del chat |
| `objective:add` | `objetivo completo` | Nuevo objetivo creado |
| `objective:complete` | `{ id, completed }` | Estado del objetivo actualizado |
| `area:add` | `área completa` | Nueva área creada |

## Reglas / Limitaciones

- **Nick**: 2-20 chars alfanuméricos (+ espacios/guiones/ñ)
- **Mensajes**: máx 500 chars
- **Rate limit**: 300 requests/min por IP (REST)
- **Inactivos**: un usuario se marca offline a los 60s sin actualizar posición
- **Tamaño payload**: máx 32KB por mensaje WebSocket
- **Concurrentes**: máx 500 conexiones socket