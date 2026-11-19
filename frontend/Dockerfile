FROM node:24-alpine AS source
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

FROM source AS checks
CMD ["npm", "test", "--", "--maxWorkers=1"]

FROM source AS build
RUN npm run build

FROM node:24-alpine AS runtime
LABEL org.opencontainers.image.source="https://github.com/Ar000sh/sharewise"
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
