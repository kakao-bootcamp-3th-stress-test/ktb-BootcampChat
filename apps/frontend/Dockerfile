# Stage 1: Builder
FROM node:22-alpine AS builder
WORKDIR /app

# 빌드 타임 환경 변수 (NEXT_PUBLIC_*는 빌드 시 필요)
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_SOCKET_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ENV NEXT_PUBLIC_SOCKET_URL=${NEXT_PUBLIC_SOCKET_URL}
ENV NEXT_TELEMETRY_DISABLED=1

# 패키지 파일 복사
COPY package.json package-lock.json* ./

# 의존성 설치
RUN if [ -f package-lock.json ]; then \
      npm ci --ignore-scripts; \
    else \
      npm install --ignore-scripts; \
    fi && \
    npm cache clean --force

# 소스 코드 복사
COPY . .

# Next.js 빌드
RUN npm run build

# Stage 2: Runner
FROM node:22-alpine AS runner
WORKDIR /app

# 필요한 파일만 복사
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000

ENV PORT=3000
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Node.js 최적화 플래그 (부하테스트용)
CMD ["node", "server.js"]
