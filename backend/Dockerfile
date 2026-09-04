FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
# better-sqlite3 necesita compilación nativa; en alpine instalamos toolchain
RUN apk add --no-cache python3 make g++ && npm install --omit=dev

COPY . .

ENV PORT=3000
ENV NODE_ENV=production

EXPOSE 3000

# Volumen para persistir SQLite
VOLUME ["/app/data"]

CMD ["node", "server.js"]