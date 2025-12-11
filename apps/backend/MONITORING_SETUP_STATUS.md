# 모니터링 인프라 설정 현황

> 마지막 업데이트: 2024-12-11

## 📊 전체 진행 상황

| 단계 | 작업 | 상태 |
|------|------|------|
| 1-1 | Prometheus 타겟 IP placeholder 설정 | ✅ 완료 |
| 1-2 | cAdvisor job 추가 | ✅ 완료 |
| 1-3 | 프론트엔드 메트릭 (prom-client) 추가 | ✅ 완료 |
| 2 | 네트워크 연결 확인 | ⏸️ 배포 후 테스트 |
| 3 | 배포 순서대로 실행 | ⏳ 대기 |
| 4 | 검증 | ⏳ 대기 |
| 5 | 보안 설정 | ⏳ 대기 |

---

## 🔧 익스포터 현황

| 구성요소 | docker-compose | 포트 | Prometheus 설정 | 서버 IP |
|---------|----------------|------|-----------------|---------|
| MongoDB Exporter | ✅ backend/mongo.yaml | 9216 | ✅ 설정 완료 | 43.203.170.199 |
| Redis Exporter | ✅ backend/redis.yaml | 9121 | ✅ 설정 완료 | 3.36.49.219 |
| cAdvisor | ✅ backend/o11y.yaml | 8080 | ✅ 설정 완료 | localhost |
| Next.js App (prom-client) | ✅ frontend (코드 내장) | 3000 | ✅ 설정 완료 | 52.78.1.186 |
| Spring Boot App | ✅ backend/docker-compose.yaml | 5001 | ✅ 설정 완료 | 43.203.209.8 |

---

## 🌐 서버 IP 정보 (배포 시 입력)

```
# 실제 배포 시 아래 IP를 채워넣으세요
BACKEND_SERVER_IP=43.203.209.8
MONGODB_SERVER_IP=43.203.170.199
REDIS_SERVER_IP=3.36.49.219
MONITORING_SERVER_IP=3.36.94.254
FRONTEND_SERVER_IP=52.78.1.186
```

---

## 📁 수정해야 할 파일 목록

### prometheus.prod.yml
- [x] spring-boot-app 타겟 → `43.203.209.8:5001`
- [x] mongodb 타겟 → `43.203.170.199:9216`
- [x] redis 타겟 → `3.36.49.219:9121`
- [x] cadvisor → `localhost:8080` (같은 서버)
- [x] nextjs-app → `52.78.1.186:3000/api/metrics`
- [x] **실제 IP로 교체 완료!**

### node-exporters.prod.yml
- [x] Backend: `43.203.209.8:9100`
- [x] Frontend: `52.78.1.186:9100`
- [x] MongoDB: `43.203.170.199:9100`
- [x] Redis: `3.36.49.219:9100`
- [x] Monitoring: `3.36.94.254:9100`

---

## 🔴 네트워크 구성

| 파일 | 네트워크 | 서비스 |
|------|----------|--------|
| backend/docker-compose.mongo.yaml | mongo-network | mongo, mongodb-exporter |
| backend/docker-compose.redis.yaml | redis-network | redis, redis-exporter |
| backend/docker-compose.o11y.yaml | monitoring | prometheus, grafana, cadvisor |
| backend/docker-compose.yaml | backend-network | backend |
| frontend/docker-compose.yaml | frontend-network | frontend |

**⚠️ 주의**: Prometheus가 다른 네트워크의 익스포터에 접근하려면 호스트 IP 또는 실제 서버 IP 사용 필요

---

## ✅ 검증 체크리스트

배포 후 확인:
- [ ] `curl http://43.203.170.199:9216/metrics` - MongoDB Exporter
- [ ] `curl http://3.36.49.219:9121/metrics` - Redis Exporter  
- [ ] `curl http://3.36.94.254:8080/metrics` - cAdvisor
- [ ] `curl http://52.78.1.186:3000/api/metrics` - Next.js App (prom-client)
- [ ] Prometheus UI (http://3.36.94.254:9090) → Status → Targets 모두 UP
- [ ] Grafana (http://3.36.94.254:3000) 대시보드 메트릭 표시 확인

---

## 🔒 보안 권장사항

익스포터 포트(9216, 9121, 8080, 9100)는 **내부 네트워크에서만 접근 가능**하도록 보안그룹 설정

---

## 🚀 배포 워크플로우

| 워크플로우 | 대상 서버 | 트리거 경로 |
|-----------|----------|------------|
| `deploy-backend.yml` | BACKEND_SERVER | `apps/backend/**` |
| `deploy-frontend.yml` | FRONTEND_SERVER | `apps/frontend/**` |
| `deploy-monitoring.yml` | MONITORING_SERVER | `apps/backend/monitoring/**`, `docker-compose.o11y.yaml` |
| `deploy-mongodb.yml` | MONGODB_SERVER | `docker-compose.mongo.yaml` |
| `deploy-redis.yml` | REDIS_SERVER | `docker-compose.redis.yaml` |

### 필요한 GitHub Secrets

| Secret 이름 | 설명 | 상태 |
|------------|------|------|
| `SSH_PRIVATE_KEY` | SSH 접속 키 | 기존 사용 |
| `MONITORING_SERVER_IP` | 모니터링 서버 IP (3.36.94.254) | 신규 추가 필요 |
| `MONGODB_SERVER_IP` | MongoDB 서버 IP (43.203.170.199) | 신규 추가 필요 |
| `REDIS_SERVER_IP` | Redis 서버 IP (3.36.49.219) | 신규 추가 필요 |
| `GRAFANA_ADMIN_USER` | Grafana 관리자 계정 (선택) | 신규 추가 권장 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 비밀번호 (선택) | 신규 추가 권장 |

### 배포 순서
```bash
# 자동 배포 (ci-cd 브랜치에 push 시)
# 또는 GitHub Actions에서 수동 실행 (workflow_dispatch)

# 워크플로우가 수행하는 작업:
# 1. monitoring/ 폴더 전체를 서버로 SCP 전송
# 2. docker-compose.o11y.yaml 전송
# 3. docker compose up -d --force-recreate 실행
```

