FROM node:22-slim
WORKDIR /app
COPY package.json ./
COPY src ./src
COPY migrations ./migrations
COPY public ./public
COPY scripts ./scripts
ENV NODE_ENV=production PORT=8080 DB_PATH=/data/app.db AUDIO_DIR=/data/audio
RUN mkdir -p /data && chown node:node /data
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "--disable-warning=ExperimentalWarning", "src/server.js"]
