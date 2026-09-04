# 🚀 Deploy del backend en Belmo (GRATIS, sin tarjeta, siempre activo)

Belmo es la única plataforma PaaS que en 2026 ofrece **1 servicio Node.js siempre activo (never-sleeps), sin tarjeta de crédito y con soporte nativo para WebSockets/Socket.IO** — el caso exacto de una app de tracking GPS en tiempo real.

**URL del servicio:** Belmo, además, se conecta **directamente a tu repositorio de GitHub**, así que no hay que tocar casi nada.

---

## ⚙️ Requisitos previos

- [ ] Repositorio `airsoft` en GitHub con el backend en `backend/` (ya está: `seguritechpty-art/airsoft`)
- [ ] El backend ya lee `PORT` del entorno (hecho en `server.js:30`)
- [ ] `package.json` tiene script `start` = `node server.js` (hecho)

---

## 🚀 Pasos exactos

### 1. Ir a Belmo
Abre **[belmo.io](https://belmo.io)** y pulsa **Join / Early access** (o el botón de GitHub) para conectarte.

### 2. Conectar cuenta de GitHub
Autoriza a Belmo (GitHub App) para que pueda leer tus repos y desplegar.

### 3. Crear el servicio
- **Connect a GitHub repo** → elige `airsoft` (tu repo).
- Elige la **rama `main`**, y por defecto la carpeta raíz.
  - ⚠️ **Importante:** tu código está en la subcarpeta `backend/`. Si Belmo no detecta el `package.json` por estar anidado, selecciona **la carpeta `backend/`** como raíz del build/root directory.
- **Framework auto-detectado:** Express/Node → `npm install` + `node server.js`.

### 4. Editar variables de entorno (si pidiera)
Belmo inyecta `PORT` automáticamente. Opcional:
| Variable | Valor | Nota |
|---|---|---|
| `DB_PATH` | (vacío) | Por defecto usa `./data.db` junto al server |
| `NODE_ENV` | `production` | Opcional |

### 5. Deploy
Pulsa **Deploy**. En 2-4 min verás:
- URL HTTPS: `https://tuNombre.onbelmo.app` (o similar)
- Certificado SSL automático
- **Sin sleep**: el proceso queda corriendo 24/7

### 6. Verificar
```bash
# Health check (debe responder {"status":"ok"})
curl https://tuNombre.onbelmo.app/health

# (Opcional) Crear una sala de prueba
curl -X POST https://tuNombre.onbelmo.app/api/squad/create \
  -H "Content-Type: application/json" \
  -d '{"nick":"test"}'
```
Debe devolver un `squadCode`.

---

## 📱 Conectar la app Android al backend de Belmo

En `android/gradle.properties` pon tu URL de Belmo en producción:

```properties
# Producción (server de Belmo)
API_BASE_URL_PROD=https://tuNombre.onbelmo.app
```

Socket.IO detecta automáticamente el `https` → usa `wss://` (WebSocket seguro) para el tiempo real. Sin cambio de código.

---

## ⚠️ Advertencias (importantes)

1. **Carpeta raíz del build:** Si Belmo usa la raíz del repo y no encuentra `package.json` (está en `backend/`), el deploy fallará con "no package.json". Solución: indicar **Root Directory = `backend/`** en la config del servicio.

2. **better-sqlite3 (dependencia nativa):** se compila durante `npm install`. Belmo soporta build tools estándar de Node. Si falla la compilación, lo más común es que falte `build-essential`/`python`; avísame y ajustamos.

3. **SQLite en disco efímero:** el backend guarda partidas en `data.db` local. Si Belmo reinicia el contenedor, se pierden las partidas activas (normal para un MVP; no es un problema real para uso en partidas). Para persistir datos reales habría que usar un volumen/Belmo managed DB (de pago) — no necesario ahora.

4. **Un solo servicio gratis:** el plan Starter cubre 1 servicio. Suficiente para una app de Airsoft de 25-30 miembros.

---

## ¿Por qué Belmo y no otros?

| | Belmo | Render | Railway | Oracle |
|---|---|---|---|---|
| Sin tarjeta | ✅ | ✅ | ❌ | ⚠️ pide tarjeta al alta |
| **Nunca duerme** | ✅ | ❌ (15min) | ✅ (hasta $) | ✅ |
| WebSocket/Socket.IO 24/7 | ✅ | ❌ (solo pago) | ✅ | ✅ |
| Deploy desde GitHub | ✅ 2-4 min | ✅ | ✅ | ❌ manual |
| Coste | **$0 para siempre** | $0 (pero duerme) | $ (crédito) | $0 |

Para un servidor de **tiempo real que no puede dormirse**, Belmo es la opción más rápida y sin fricción.