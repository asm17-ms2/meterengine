# 프론트엔드 실행 이미지. MS2-166의 운영 compose가 이 이미지를 띄운다.
#
# 테스트와 lint는 CI(MS2-167)가 돌린다. 여기서는 빌드만 한다.
FROM node:24-alpine AS build
WORKDIR /app

# pnpm 버전은 package.json의 packageManager 필드가 정본이다. corepack이 그 값을 읽어
# 같은 버전을 설치하므로 여기에 버전을 한 번 더 적지 않는다(적으면 두 곳이 갈라진다).
RUN corepack enable pnpm

# 소스보다 훨씬 드물게 바뀌는 의존성 정의를 먼저 복사해 install 레이어를 캐시한다.
# 소스만 고친 빌드는 이 레이어를 재사용한다.
# pnpm-workspace.yaml도 같이 복사한다. 이 레포에서는 워크스페이스 선언이 아니라
# postinstall 스크립트 허용 목록(allowBuilds)이라, 빠지면 sharp가 빌드되지 않는다.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

# 백엔드가 없어도 통과해야 한다. 조회 fetch가 전부 cache: "no-store"라 빌드 타임에
# 나가지 않는다(src/lib/api/client.ts).
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm build

# 실행 이미지에는 standalone 출력만 담는다. pnpm, 소스, devDependencies가 빠진다.
# standalone은 next build가 실제로 도달하는 모듈만 추려 담은 self-contained 서버다.
FROM node:24-alpine
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# standalone 서버는 이 둘을 런타임에 읽는다. HOSTNAME을 비우면 localhost에 바인딩해
# 컨테이너 밖에서 닿지 않는다.
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# standalone 출력에는 정적 자산이 들어 있지 않다. 따로 넣지 않으면 화면은 뜨는데
# CSS와 JS 청크만 404가 난다.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

# public/을 만들면 여기에 COPY 한 줄을 더한다. 지금은 그 디렉터리가 없다.

# node 이미지가 미리 만들어 둔 비root 사용자다.
USER node

EXPOSE 3000

# 백엔드 주소와 도입사 식별자는 이미지에 굽지 않고 런타임 환경변수로 받는다
# (METERENGINE_API_BASE_URL, METERENGINE_ORGANIZATION_ID, METERENGINE_ORGANIZATION_NAME).
# 주지 않으면 src/lib/config.ts의 로컬 개발용 기본값으로 떨어진다.
CMD ["node", "server.js"]
