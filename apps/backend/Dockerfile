# Otter backend for a NAS: one Go binary plus the plugin bundles.
# Build from the repo root (the plugin bundler needs packages/otter-sdk):
#
#   docker build -f apps/backend/Dockerfile -t otter-backend \
#     --secret id=tmdb_api_key,env=TMDB_API_KEY .
#
# The secret bakes in the built-in TMDB key without leaving it in the image
# history; without it the app asks for a key on first start.

FROM oven/bun:1 AS plugins
WORKDIR /src
COPY packages/otter-sdk packages/otter-sdk
COPY apps/backend/scripts apps/backend/scripts
COPY apps/backend/plugins apps/backend/plugins
# Plugins only import the SDK at bundle time; node-html-parser is provided
# natively by the backend's runtime.
RUN mkdir -p node_modules/@neoworks-dev \
 && ln -s ../../packages/otter-sdk node_modules/@neoworks-dev/otter-sdk \
 && cd apps/backend && bun scripts/bundle-plugins.ts --release --out /plugins

FROM golang:1.26-alpine AS server
WORKDIR /src
COPY apps/backend/go.mod apps/backend/go.sum ./
RUN go mod download
COPY apps/backend/cmd cmd
COPY apps/backend/internal internal
RUN --mount=type=secret,id=tmdb_api_key \
    key="$(cat /run/secrets/tmdb_api_key 2>/dev/null || true)" \
 && CGO_ENABLED=0 go build -trimpath \
    -ldflags="-s -w -X github.com/neoworks/otter/internal/settings.DefaultTMDBApiKey=${key}" \
    -o /otter ./cmd/server

FROM alpine:3
# Hooks are shell scripts; bash and jq make them pleasant to write.
RUN apk add --no-cache ca-certificates bash jq curl
COPY --from=server /otter /usr/local/bin/otter
COPY --from=plugins /plugins /opt/otter/plugins
ENV DATA_DIR=/data \
    PLUGIN_DIR=/opt/otter/plugins \
    PORT=8080 \
    DNS_SERVERS=1.1.1.1,8.8.8.8
VOLUME /data
EXPOSE 8080
CMD ["otter"]
