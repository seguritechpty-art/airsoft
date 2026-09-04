# 📦 Despliegue del Backend (Gratis / Siempre Disponible)

> **Objetivo**: el backend debe estar SIEMPRE ONLINE para que el tracking
> en tiempo real funcione. Nada de "sleep after 15min".

## Opciones comparadas (2026)

| Plataforma | Coste | Siempre-on? | WebSocket? | Veredicto |
|---|---|---|---|---|
| **Oracle Cloud Always Free** | **$0** | ✅ | ✅ | 🏆 **MEJOR para este proyecto** |
| Railway (Hobby) | ~$0-5/mes | ✅ | ✅ | Samsung alternativo, muy fácil |
| Fly.io | ~$2-4/mes | ✅ | ✅ Excelente | Bien para latencia global |
| Render (Free) | $0 | ❌ **Duerme a los 15 min** | ✅ | ❌ No sirve para tracking real-time |

## 🏆 RECOMENDADO: Oracle Cloud Always Free

Recursos gratis **para siempre**: 4 vCPU ARM + 24GB RAM + 200GB disco + 10TB tráfico.
Sobrado para 25-30 usuarios.

### Pasos

1. Crea cuenta en [Oracle Cloud](https://www.oracle.com/cloud/free/) (pide tarjeta para verificar, no cobra nada)
2. Ve a **Compute → Instances → Create Instance**
3. Elige imagen **Ubuntu 22.04/24.04** con arquitectura **ARM** (Ampere A1, el free)
4. Abre el puerto **3000/tcp** en el Security List (Ingress Rules)
5. Conecta por SSH y ejecuta:

```bash
# Docker + compose
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# (reconectar la sesión SSH)

# Clonar y desplegar
git clone https://github.com/TU_USUARIO/airsoft-tracker.git
cd airsoft-tracker/backend
docker compose up -d --build

# Verificar
curl http://TU_IP:3000/health
```

6. El servidor queda corriendo con `restart: unless-stopped` → **siempre online**

> 💡 Para IP fija, asigna un **Public IP (Reserved)** gratuito en Oracle.

## Alternativa: Railway (más fácil, sin servidor)

1. Crear cuenta en [Railway](https://railway.app)
2. **New Project → Deploy from GitHub repo** (selecciona `airsoft-tracker/backend`)
3. Railway detecta el Dockerfile automáticamente
4. **Generate Domain** → obtienes `https://backend-production-xxxx.up.railway.app`
5. Configurar variable:

```
DB_PATH = /data/data.db   (puede requerir un volumen)
```

6. Añadir un **Volume** de 1GB montado en `/data` para persistir SQLite

## Configurar la APP Android

En `android/gradle.properties`:

```properties
# Producción - URL de tu servidor desplegado
API_BASE_URL_PROD=https://backend-production-xxxx.up.railway.app
```

Luego compila el APK **release**:

```bash
cd android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## ⚠️ Nota importante

**El tracking usa HTTP en debug** (`http://10.0.2.2:3000`). Para producción,
Railway/Oracle dan **HTTPS automático**, requerido por Android para conexiones
cleartext en release. Si despliegas en otro sitio sin TLS, añade en el manifest:

```xml
<application android:usesCleartextTraffic="true">
```

(aunque no es recomendable - los operadores móviles bloquean HTTP en algunos países).