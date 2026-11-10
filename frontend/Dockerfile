# Frontend image. Build from this folder: docker build -t sharewise-frontend frontend
# The image holds no environment-specific values: BACKEND_URL, KEYCLOAK_ISSUER, KEYCLOAK_CLIENT_ID and APP_URL are
# read by the server when it runs (see frontend/.env.example), PORT defaults to 3000.

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules node_modules
COPY . .
RUN npm run build

FROM node:24-alpine
LABEL org.opencontainers.image.source="https://github.com/Ar000sh/sharewise"
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
# Listen on every interface: the standalone server otherwise binds to the container's hostname only.
ENV HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static .next/static
COPY --from=build /app/public public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
