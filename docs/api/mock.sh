#!/usr/bin/env sh
# Starts a Prism mock server for the ShareWise API contract on port 4010.
# Point the frontend at it with: BACKEND_URL=http://localhost:4010 npm run dev
cd "$(dirname "$0")" || exit 1
exec npx --yes @stoplight/prism-cli mock openapi.yaml -p 4010
