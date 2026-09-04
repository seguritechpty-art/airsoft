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

> ✅ **Actualizado**: el repo ahora está preparado para desplegar en Belmo con la config MÍNIMA.
> Ya NO hay `Dockerfile` en `backend/` (Belmo Starter/free NO soporta Dockerfile builds, por eso
> fallaba: si detecta un Dockerfile en la raíz configurada, intenta Docker y el plan free lo rechaza).
> Los archivos de Docker se movieron a `deploy/` (solo para deploy local/Oracle).

### 1. Ir a Belmo
Abre **[belmo.io](https://belmo.io)** o directo **dashboard.belmo.io** y entra a tu cuenta (ya autorizada).

### 2. Crear/editar el servicio (tipo **API / Web Service**)
- **Repo**: `airsoft`
- **Branch**: `main`
- **Root Directory**: usa **`backend/`** (recomendado, es donde vive el código). 
  *Alternativa:* ahora hay `package.json` en la raíz, así que `./` también sirve.
- **Build Command**: *(vacío)* → Belmo hace `npm install`
- **Start Command**: *(vacío)* → Belmo usa `npm start` (= `node server.js`)
- **Health Check Path**: `/health`
- **Auto-Deploy**: ON

### 3. Variables de entorno (opcional)
| Variable | Valor |
|---|---|
| `NODE_ENV` | `production` |

### 4. Deploy
Pulsa **Deploy**. En 2-4 min:
- URL: `https://TU-SERVICIO.app.belmo.io` (Starter: wildcard subdomain)
- SSL automático, **no duerme nunca**

### 5. Verificar
```bash
curl https://TU-SERVICIO.app.belmo.io/health   # → {"status":"ok",...}
```

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

2. **SQLite nativo (node:sqlite):** el backend usa el motor SQLite integrado en Node 22.5+ (`node:sqlite`), **sin dependencias nativas que compilar**. `npm install` solo instala paquetes JS puros → el build de Belmo no puede fallar por binarios. Si Belmo desplegara con un Node < 22.5, el server arrancaría en modo memoria (también funciona).

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