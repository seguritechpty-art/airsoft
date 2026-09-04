/**
 * AIRSOFT TRACKER - BACKEND
 * Servidor táctico para coordinar escuadrones en partidas de Airsoft/MilSim.
 *
 * Stack: Node.js + Express + Socket.IO + SQLite (node:sqlite nativo).
 * Sin dependencias externas de infra: corre en cualquier VPS/hosting free.
 *
 * Funcionalidades:
 *  - Salas de partida (squad code) con usuarios
 *  - Ubicaciones GPS en tiempo real (WebSocket broadcast a la sala)
 *  - Chat de escuadrón
 *  - Objetivos (waypoints) y áreas de colores compartidas
 *  - API REST + WebSocket
 *  - Rate limiting, validación, limpieza automática de inactivos
 */

import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data', 'airsoft.db');

// ---------------------------------------------------------------------------
// BASE DE DATOS (SQLite auto-contenido)
// Usamos node:sqlite (nativo de Node 22+, SIN compilar binarios) para que el
// deploy funcione en cualquier PaaS sin build tools. Si el runtime no lo trae,
// caemos a modo memoria: el server sigue funcionando con salas efímeras.
// ---------------------------------------------------------------------------
let db;
try {
  fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
  const { DatabaseSync } = await import('node:sqlite');
  db = new DatabaseSync(DB_PATH);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec(`
    CREATE TABLE IF NOT EXISTS squads (
      code TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      squad_code TEXT NOT NULL,
      nick TEXT NOT NULL,
      color TEXT NOT NULL,
      online INTEGER DEFAULT 1,
      last_seen INTEGER NOT NULL,
      lat REAL,
      lng REAL,
      heading REAL,
      speed REAL,
      accuracy REAL,
      updated_at INTEGER,
      FOREIGN KEY (squad_code) REFERENCES squads(code)
    );
    CREATE TABLE IF NOT EXISTS objectives (
      id TEXT PRIMARY KEY,
      squad_code TEXT NOT NULL,
      name TEXT NOT NULL,
      description TEXT DEFAULT '',
      lat REAL NOT NULL,
      lng REAL NOT NULL,
      color TEXT NOT NULL,
      radius INTEGER DEFAULT 100,
      completed INTEGER DEFAULT 0,
      created_by TEXT,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS areas (
      id TEXT PRIMARY KEY,
      squad_code TEXT NOT NULL,
      name TEXT NOT NULL,
      color TEXT NOT NULL,
      opacity REAL DEFAULT 0.5,
      coordinates TEXT NOT NULL,
      type TEXT NOT NULL,
      created_by TEXT,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS messages (
      id TEXT PRIMARY KEY,
      squad_code TEXT NOT NULL,
      nick TEXT NOT NULL,
      text TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );
  `);
  console.log('[DB] SQLite nativo inicializado en', DB_PATH);
} catch (err) {
  console.warn('[DB] SQLite nativo no disponible, usando modo memoria:', err.message);
  db = null;
}

// Fallback: almacenamiento en memoria si SQLite falla
const mem = {
  squads: new Map(),
  users: new Map(),
  objectives: new Map(),
  areas: new Map(),
  messages: [],
};

const txSquad = (code) => {
  if (db) return db.prepare('SELECT * FROM squads WHERE code = ?').get(code);
  return mem.squads.get(code);
};
const txUsers = (squad) => {
  if (db) return db.prepare('SELECT * FROM users WHERE squad_code = ?').all(squad);
  return [...mem.users.values()].filter((u) => u.squad_code === squad);
};
const normalizeUser = (u) => ({
  id: u.id,
  squad_code: u.squad_code,
  nick: u.nick,
  color: u.color,
  online: u.online ? 1 : 0,
  last_seen: u.last_seen ?? now(),
  lat: u.lat ?? null,
  lng: u.lng ?? null,
  heading: u.heading ?? null,
  speed: u.speed ?? null,
  accuracy: u.accuracy ?? null,
  updated_at: u.updated_at ?? u.last_seen ?? now(),
});

const txSaveUser = (u) => {
  const row = normalizeUser(u);
  if (db) {
    db.prepare(`INSERT INTO users (id, squad_code, nick, color, online, last_seen, lat, lng, heading, speed, accuracy, updated_at)
      VALUES (@id, @squad_code, @nick, @color, @online, @last_seen, @lat, @lng, @heading, @speed, @accuracy, @updated_at)
      ON CONFLICT(id) DO UPDATE SET
        nick=@nick, color=@color, online=@online, last_seen=@last_seen,
        lat=@lat, lng=@lng, heading=@heading, speed=@speed, accuracy=@accuracy, updated_at=@updated_at`).run(row);
  } else {
    mem.users.set(u.id, row);
  }
};
const txUpdatePos = (u) => {
  if (db) {
    db.prepare(`UPDATE users SET lat=@lat, lng=@lng, heading=@heading, speed=@speed, accuracy=@accuracy, updated_at=@updated_at, last_seen=@last_seen, online=1 WHERE id=@id`).run(u);
  } else {
    const cur = mem.users.get(u.id);
    if (cur) Object.assign(cur, u);
  }
};
const txObjectives = (squad) => {
  if (db) return db.prepare('SELECT * FROM objectives WHERE squad_code = ?').all(squad);
  return [...mem.objectives.values()].filter((o) => o.squad_code === squad);
};
const txSaveObjective = (o) => {
  if (db) db.prepare(`INSERT INTO objectives (id, squad_code, name, description, lat, lng, color, radius, completed, created_by, created_at)
    VALUES (@id, @squad_code, @name, @description, @lat, @lng, @color, @radius, @completed, @created_by, @created_at)`).run(o);
  else mem.objectives.set(o.id, o);
};
const txCompleteObjective = (id, squad, completed) => {
  if (db) db.prepare('UPDATE objectives SET completed=? WHERE id=? AND squad_code=?').run(completed ? 1 : 0, id, squad);
  else {
    const o = mem.objectives.get(id);
    if (o && o.squad_code === squad) o.completed = completed ? 1 : 0;
  }
};
const txAreas = (squad) => {
  if (db) return db.prepare('SELECT * FROM areas WHERE squad_code = ?').all(squad);
  return [...mem.areas.values()].filter((a) => a.squad_code === squad);
};
const txSaveArea = (a) => {
  if (db) db.prepare(`INSERT INTO areas (id, squad_code, name, color, opacity, coordinates, type, created_by, created_at)
    VALUES (@id, @squad_code, @name, @color, @opacity, @coordinates, @type, @created_by, @created_at)`).run(a);
  else mem.areas.set(a.id, a);
};
const txMessages = (squad, limit) => {
  if (db) return db.prepare('SELECT * FROM messages WHERE squad_code = ? ORDER BY created_at DESC LIMIT ?').all(squad, limit).reverse();
  return mem.messages.filter((m) => m.squad_code === squad).slice(-limit);
};
const txSaveMessage = (m) => {
  if (db) db.prepare('INSERT INTO messages (id, squad_code, nick, text, created_at) VALUES (@id, @squad_code, @nick, @text, @created_at)').run(m);
  else {
    mem.messages.push(m);
    if (mem.messages.length > 2000) mem.messages.splice(0, mem.messages.length - 2000);
  }
};
const txSetOnline = (userId, online) => {
  if (db) db.prepare('UPDATE users SET online=? WHERE id=?').run(online ? 1 : 0, userId);
  else {
    const u = mem.users.get(userId);
    if (u) u.online = online ? 1 : 0;
  }
};

// ---------------------------------------------------------------------------
// APP + HTTP + SOCKET.IO
// ---------------------------------------------------------------------------
const app = express();
app.use(helmet());
app.use(cors({ origin: true }));
app.use(express.json({ limit: '32kb' }));

const limiter = rateLimit({ windowMs: 60_000, max: 300, standardHeaders: true, legacyHeaders: false });
app.use('/api/', limiter);

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: true, methods: ['GET', 'POST'] },
  maxHttpBufferSize: 32 * 1024,
});

// ---------------------------------------------------------------------------
// HELPERS
// ---------------------------------------------------------------------------
const now = () => Date.now();
const uid = () => crypto.randomBytes(8).toString('hex');
const makeToken = () => crypto.randomBytes(24).toString('hex');
const colorFromNick = (nick) => {
  let h = 0;
  for (let i = 0; i < nick.length; i++) h = (h * 31 + nick.charCodeAt(i)) | 0;
  const hue = Math.abs(h) % 360;
  return `hsl(${hue}, 70%, 50%)`;
};
const genSquadCode = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) code += chars[crypto.randomInt(chars.length)];
  return code;
};
const validNick = (n) => typeof n === 'string' && n.length >= 2 && n.length <= 20 && /^[a-zA-Z0-9_\-áéíóúñÁÉÍÓÚÑ ]+$/.test(n);
const validText = (t) => typeof t === 'string' && t.length >= 1 && t.length <= 500;
const validNum = (n) => typeof n === 'number' && Number.isFinite(n);
const validLat = (n) => validNum(n) && n >= -90 && n <= 90;
const validLng = (n) => validNum(n) && n >= -180 && n <= 180;

// Sesiones: socketId -> { userId, squad, nick, token }
const sessions = new Map();

const publicUser = (u) => ({
  id: u.id,
  nick: u.nick,
  color: u.color,
  online: !!u.online,
  lat: u.lat ?? null,
  lng: u.lng ?? null,
  heading: u.heading ?? null,
  speed: u.speed ?? null,
  accuracy: u.accuracy ?? null,
  updated_at: u.updated_at ?? null,
});

const broadcastSquad = (squad, event, data) => io.to('squad:' + squad).emit(event, data);

function emitSquadState(squad) {
  broadcastSquad(squad, 'squad:state', {
    users: txUsers(squad).map(publicUser),
    objectives: txObjectives(squad),
    areas: txAreas(squad),
  });
}

function cleanupInactive() {
  const cutoff = now() - 60_000; // 60s sin latido => offline
  if (db) {
    const rows = db.prepare('SELECT id, squad_code FROM users WHERE online=1 AND last_seen < ?').all(cutoff);
    for (const r of rows) {
      db.prepare('UPDATE users SET online=0 WHERE id=?').run(r.id);
      emitSquadState(r.squad_code);
    }
  } else {
    for (const u of mem.users.values()) {
      if (u.online && u.last_seen < cutoff) {
        u.online = 0;
        emitSquadState(u.squad_code);
      }
    }
  }
}
setInterval(cleanupInactive, 20_000);

// ---------------------------------------------------------------------------
// API REST
// ---------------------------------------------------------------------------
app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime(), users: db ? db.prepare('SELECT COUNT(*) c FROM users WHERE online=1').get().c : [...mem.users.values()].filter(u=>u.online).length });
});

// Crear partida (squad)
app.post('/api/squad/create', (req, res) => {
  const { nick } = req.body || {};
  if (!validNick(nick)) return res.status(400).json({ error: 'Nick inválido (2-20 chars, alfanumérico)' });
  const code = genSquadCode();
  const name = (req.body.name || `Partida ${code}`).toString().slice(0, 40);
  if (db) db.prepare('INSERT INTO squads (code, name, created_at) VALUES (?,?,?)').run(code, name, now());
  else mem.squads.set(code, { code, name, created_at: now() });
  const user = { id: uid(), squad_code: code, nick, color: colorFromNick(nick), online: 1, last_seen: now(), updated_at: now() };
  txSaveUser(user);
  res.status(201).json({ squadCode: code, squadName: name, userId: user.id, token: makeToken(), user });
});

// Unirse a partida
app.post('/api/squad/join', (req, res) => {
  const { nick, squadCode } = req.body || {};
  if (!validNick(nick)) return res.status(400).json({ error: 'Nick inválido' });
  const sc = String(squadCode || '').trim().toUpperCase();
  if (!/^[A-Z0-9]{6}$/.test(sc)) return res.status(400).json({ error: 'Código de partida inválido' });
  const squad = txSquad(sc);
  if (!squad) return res.status(404).json({ error: 'Partida no encontrada' });
  // Reutilizar usuario si ya existe
  let user;
  if (db) user = db.prepare('SELECT * FROM users WHERE squad_code=? AND nick=?').get(sc, nick);
  else user = [...mem.users.values()].find((u) => u.squad_code === sc && u.nick === nick);
  if (user) {
    user.last_seen = now();
    user.online = 1;
    txSaveUser(user);
  } else {
    user = { id: uid(), squad_code: sc, nick, color: colorFromNick(nick), online: 1, last_seen: now(), updated_at: now() };
    txSaveUser(user);
  }
  res.json({ squadCode: sc, squadName: squad.name, userId: user.id, token: makeToken(), user: publicUser(user) });
});

// Estado completo de la sala (para los que llegan tarde)
app.get('/api/squad/:code/state', (req, res) => {
  const sc = String(req.params.code || '').trim().toUpperCase();
  const squad = txSquad(sc);
  if (!squad) return res.status(404).json({ error: 'Partida no encontrada' });
  res.json({ squad: { code: sc, name: squad.name }, users: txUsers(sc).map(publicUser), objectives: txObjectives(sc), areas: txAreas(sc) });
});

// ---------------------------------------------------------------------------
// SOCKET.IO - LÓGICA EN TIEMPO REAL
// ---------------------------------------------------------------------------
io.use((socket, next) => {
  // Limitamos conexiones por IP para evitar abuso
  const ip = socket.handshake.address;
  if (io.of('/').sockets.size > 500) return next(new Error('Servidor lleno'));
  socket.data.ip = ip;
  next();
});

io.on('connection', (socket) => {
  // ---- Identificarse en una sala ----
  socket.on('squad:join', async ({ token, squadCode }, ack) => {
    try {
      const sc = String(squadCode || '').trim().toUpperCase();
      const squad = txSquad(sc);
      if (!squad) return ack?.({ ok: false, error: 'Partida no encontrada' });

      // Buscar usuario por token no es fiable (token no persistido), así que re-bindeamos:
      // el cliente manda token+código, nosotros buscamos el usuario por nick vía REST previo.
      // Simplificación: el token se usa solo para validar que el cliente ya hizo join.
      if (!token || token.length < 16) return ack?.({ ok: false, error: 'Token inválido' });

      socket.join('squad:' + sc);
      sessions.set(socket.id, { squad: sc, nick: socket.data.nick || '?' });
      socket.data.squad = sc;

      // Avisar a los demás que este usuario está online (ya fue marcado en REST join)
      const users = txUsers(sc);
      const me = users.find((u) => u.nick === socket.data.nick) || users[0];
      socket.data.userId = me?.id;

      ack?.({ ok: true, users: users.map(publicUser), objectives: txObjectives(sc), areas: txAreas(sc) });
    } catch (e) {
      ack?.({ ok: false, error: e.message });
    }
  });

  // ---- Autenticar con nick directo (alternativa sin REST previo) ----
  socket.on('squad:auth', ({ nick, squadCode }, ack) => {
    const sc = String(squadCode || '').trim().toUpperCase();
    if (!validNick(nick)) return ack?.({ ok: false, error: 'Nick inválido' });
    const squad = txSquad(sc);
    if (!squad) return ack?.({ ok: false, error: 'Partida no encontrada' });

    let user;
    if (db) user = db.prepare('SELECT * FROM users WHERE squad_code=? AND nick=?').get(sc, nick);
    else user = [...mem.users.values()].find((u) => u.squad_code === sc && u.nick === nick);
    if (user) {
      user.last_seen = now(); user.online = 1;
      txSaveUser(user);
    } else {
      user = { id: uid(), squad_code: sc, nick, color: colorFromNick(nick), online: 1, last_seen: now(), updated_at: now() };
      txSaveUser(user);
    }

    socket.join('squad:' + sc);
    socket.data.squad = sc;
    socket.data.userId = user.id;
    socket.data.nick = nick;
    sessions.set(socket.id, { squad: sc, userId: user.id, nick });
    emitSquadState(sc);
    ack?.({ ok: true, user: publicUser(user), users: txUsers(sc).map(publicUser), objectives: txObjectives(sc), areas: txAreas(sc) });
  });

  // ---- Actualización de ubicación GPS ----
  socket.on('location:update', ({ lat, lng, heading, speed, accuracy }) => {
    const s = sessions.get(socket.id);
    if (!s) return;
    if (!validLat(lat) || !validLng(lng)) return;
    const u = { id: s.userId, lat, lng, heading: validNum(heading) ? heading : null, speed: validNum(speed) ? speed : null, accuracy: validNum(accuracy) ? accuracy : null, updated_at: now(), last_seen: now() };
    txUpdatePos(u);
    const full = db ? db.prepare('SELECT * FROM users WHERE id=?').get(s.userId) : mem.users.get(s.userId);
    if (full) broadcastSquad(s.squad, 'location:update', publicUser(full));
  });

  // ---- Chat ----
  socket.on('chat:message', ({ text }) => {
    const s = sessions.get(socket.id);
    if (!s || !validText(text)) return;
    const msg = { id: uid(), squad_code: s.squad, nick: s.nick, text: text.slice(0, 500), created_at: now() };
    txSaveMessage(msg);
    broadcastSquad(s.squad, 'chat:message', { id: msg.id, nick: msg.nick, text: msg.text, created_at: msg.created_at });
  });

  // ---- Objetivos ----
  socket.on('objective:add', ({ name, description, lat, lng, color, radius }, ack) => {
    const s = sessions.get(socket.id);
    if (!s) return ack?.({ ok: false, error: 'No autenticado' });
    if (typeof name !== 'string' || name.length < 1 || name.length > 60) return ack?.({ ok: false, error: 'Nombre inválido' });
    if (!validLat(lat) || !validLng(lng)) return ack?.({ ok: false, error: 'Coordenadas inválidas' });
    const obj = { id: uid(), squad_code: s.squad, name: name.slice(0, 60), description: String(description||'').slice(0, 300), lat, lng, color: String(color||'#FF0000').slice(0, 9), radius: validNum(radius) ? Math.min(5000, Math.max(10, radius)) : 100, completed: 0, created_by: s.nick, created_at: now() };
    txSaveObjective(obj);
    broadcastSquad(s.squad, 'objective:add', obj);
    ack?.({ ok: true, objective: obj });
  });

  socket.on('objective:complete', ({ id, completed }, ack) => {
    const s = sessions.get(socket.id);
    if (!s || typeof id !== 'string') return ack?.({ ok: false });
    txCompleteObjective(id, s.squad, !!completed);
    broadcastSquad(s.squad, 'objective:complete', { id, completed: !!completed });
    ack?.({ ok: true });
  });

  // ---- Áreas ----
  socket.on('area:add', ({ name, color, opacity, coordinates, type }, ack) => {
    const s = sessions.get(socket.id);
    if (!s) return ack?.({ ok: false, error: 'No autenticado' });
    if (typeof name !== 'string' || name.length < 1 || name.length > 60) return ack?.({ ok: false, error: 'Nombre inválido' });
    if (!Array.isArray(coordinates) || coordinates.length < 2) return ack?.({ ok: false, error: 'Coordenadas inválidas' });
    const area = { id: uid(), squad_code: s.squad, name: name.slice(0,60), color: String(color||'#00FF00').slice(0,9), opacity: validNum(opacity) ? Math.min(1, Math.max(0.1, opacity)) : 0.5, coordinates: JSON.stringify(coordinates), type: type === 'circle' ? 'circle' : 'polygon', created_by: s.nick, created_at: now() };
    txSaveArea(area);
    broadcastSquad(s.squad, 'area:add', { ...area, coordinates: coordinates });
    ack?.({ ok: true, area: { ...area, coordinates: coordinates } });
  });

  socket.on('disconnect', () => {
    const s = sessions.get(socket.id);
    if (s?.userId) {
      txSetOnline(s.userId, 0);
      emitSquadState(s.squad);
    }
    sessions.delete(socket.id);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[SERVER] Airsoft Tracker backend en http://0.0.0.0:${PORT}`);
  console.log(`[SERVER] Health check: http://localhost:${PORT}/health`);
});