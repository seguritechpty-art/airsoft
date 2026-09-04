FROM node:22-alpine

WORKDIR /app

# node:sqlite es nativo de Node 22.5+ → no hay compilación, solo npm install
COPY package*.json ./
RUN npm install --omit=dev

COPY . .

ENV PORT=3000
ENV NODE_ENV=production

EXPOSE 3000

# Volumen para persistir SQLite
VOLUME ["/app/data"]

CMD ["node", "server.js"]