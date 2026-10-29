FROM node:24.22.1-bookworm-slim AS builder
WORKDIR /app
RUN npm install --global pnpm@11.18.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY sdk/package.json ./sdk/package.json
COPY llm-service/package.json ./llm-service/package.json
RUN pnpm install --frozen-lockfile --filter @meterengine/llm-service...
COPY sdk ./sdk
COPY llm-service ./llm-service
COPY eslint-naming-rules.mjs ./eslint-naming-rules.mjs
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter @meterengine/llm-service build

FROM node:24.22.1-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production PORT=3200 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1 LUMEN_DATABASE_PATH=/data/lumen.sqlite
COPY --from=builder --chown=node:node /app/llm-service/.next/standalone ./
COPY --from=builder --chown=node:node /app/llm-service/.next/static ./llm-service/.next/static
RUN mkdir /data && chown node:node /data && chmod 700 /data
USER node
VOLUME ["/data"]
EXPOSE 3200
HEALTHCHECK --interval=30s --timeout=5s CMD node -e "fetch('http://127.0.0.1:3200/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "llm-service/server.js"]
