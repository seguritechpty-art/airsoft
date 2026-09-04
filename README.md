# 🎯 AIRSOFT TRACKER

Aplicación Android de **coordinación táctica en tiempo real** para equipos de Airsoft/MilSim.
Sabe dónde está cada compañero del escuadrón en todo momento, con objetivos, áreas de colores y chat.

```
┌───────────────────────────────┐         ┌───────────────────────────────┐
│        ANDROID APP            │         │         BACKEND              │
│  Kotlin + Jetpack Compose     │◄───────►│  Node.js + Socket.IO         │
│  Google Maps + GPS Service    │ WebSocket│  SQLite (auto-contenido)     │
│  Android 7.0+ (API 24)        │   +REST │  Desplegable en hosting free │
└───────────────────────────────┘         └───────────────────────────────┘
```

## 🚀 Características

| Feature | Descripción |
|---|---|
| 📍 **Tracking en tiempo real** | GPS cada 5s, posiciones de todo el escuadrón sin lag |
| 🗺️ **Mapa táctico** | Google Maps híbrido, marcadores con nombre/color/velocidad |
| 🎯 **Objetivos** | Crear waypoints tocando el mapa, radio de alerta, completar |
| 🔵 **Áreas de colores** | Círculos y polígonos sobre el mapa para marcar zonas |
| 💬 **Chat** | Comunicación de escuadrón incluida |
| 👥 **Salas/Partidas** | Código de 6 letras: crea tu partida o únete a otra |
| 📡 **Offline resilience** | Socket.IO con reconexión automática y fallback polling |
| 🔋 **Optimizado batería** | Foreground service con update interval configurable |

## 🏗️ Estructura del proyecto

```
airsoft-tracker/
├── android/                  # Cliente Android (Kotlin + Compose)
│   └── app/src/main/java/com/airsoft/tracker/
│       ├── MainActivity.kt           # Punto de entrada + navegación
│       ├── AirsoftApp.kt             # Application + DI simple
│       ├── data/
│       │   ├── model/Models.kt       # DTOs
│       │   ├── network/ApiService.kt # Retrofit (REST)
│       │   ├── socket/SocketManager.kt # Socket.IO (tiempo real)
│       │   ├── prefs/SessionPrefs.kt # Sesión persistente
│       │   └── repository/TrackerRepository.kt
│       ├── location/LocationTrackingService.kt  # Foreground GPS
│       └── presentation/
│           ├── MainViewModel.kt      # Estado + lógica
│           ├── screens/              # LoginScreen, MapScreen
│           └── theme/Theme.kt
├── backend/                  # Servidor (Node.js + Socket.IO + SQLite)
│   ├── server.js             # Todo el backend en un archivo
│   ├── Dockerfile            # Deploy en cualquier VPS/hosting
│   └── docker-compose.yml
└── docs/                     # Documentación
```

## 🛠️ Stack técnico

**Android**
- Kotlin 1.9 + Jetpack Compose (Material 3)
- Google Maps SDK + Maps Compose
- Socket.IO client (WebSocket con fallback polling)
- Retrofit/OkHttp (REST)
- Foreground Service con FusedLocationProviderClient
- Min SDK 24 (Android 7.0) — cubre dispositivos viejos y nuevos

**Backend**
- Node.js 18+ + Express + Socket.IO
- SQLite (better-sqlite3) — **cero dependencias de infraestructura**
- Helmet, CORS, rate-limiting, validación de entrada
- Modo memoria automático si SQLite no está disponible

## 📖 Documentación

- [📦 Despliegue del backend (free/barato)](docs/DEPLOY.md)
- [🔌 API REST + WebSocket](docs/API.md)

## 🚀 Primeros pasos

### 1. Levantar el backend (local)

```bash
cd backend
npm install
node server.js
# Health check: http://localhost:3000/health
```

### 2. Abrir la app en Android Studio

```bash
cd android
# Abrir la carpeta android/ en Android Studio
```

Requisitos:
- Android Studio Hedgehog+ (o cualquier versión 2023+)
- JDK 17
- Android SDK 34

### 3. Configurar Google Maps API Key

1. Ve a [Google Cloud Console](https://console.cloud.google.com/)
2. Crea un proyecto y habilita **Maps SDK for Android**
3. Genera una API Key
4. Ponla en `android/app/src/main/res/values/` o directamente en el Manifest:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="TU_API_KEY_AQUI" />
   ```

### 4. Configurar la URL del backend

En `android/gradle.properties` (o por flag de build):

```properties
# Para emulador (el 10.0.2.2 apunta al localhost del host)
API_BASE_URL=http://10.0.2.2:3000

# Para dispositivo físico conectado por USB (usa la IP de tu PC)
# API_BASE_URL=http://192.168.1.50:3000

# Para producción (tu servidor desplegado)
API_BASE_URL_PROD=https://tu-servidor.railway.app
```

### 5. Compilar y testear

```bash
cd android
./gradlew assembleDebug
# APK en: android/app/build/outputs/apk/debug/app-debug.apk
```

## 🎮 Flujo de uso en partida

1. **Capitán**: crea la partida → obtiene código `XC3WAX`
2. **Equipo**: cada uno entra con su nick y el código
3. Todos se ven en el mapa con colores únicos
4. Se marcan objetivos (zona A, base enemiga) y áreas de colores
5. El capitán ve quién está offline/online y las velocidades

## ⚠️ Nota de uso

Herramienta táctica para **Airsoft/MilSim legal** en campos autorizados.
No usar para vigilancia de personas sin consentimiento.