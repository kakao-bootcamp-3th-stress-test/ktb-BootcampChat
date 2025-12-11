# Redis 고가용성/샤딩 설정 가이드

Socket.IO 어댑터와 `StringRedisTemplate` 모두 `spring.data.redis.*` 설정을 공유합니다. `spring.data.redis.sentinel.*` 또는 `spring.data.redis.cluster.*` 값이 주어지면 해당 모드가 자동으로 활성화되며, `RedissonConfig` 역시 동일 구성을 따릅니다. 아래 표를 참고해 환경 변수를 설정해 주세요.

## 1. 단일 인스턴스 모드 (기본)
| 항목 | 환경 변수 | 예시 값 | 설명 |
| --- | --- | --- | --- |
| 호스트 | `REDIS_HOST` | `redis-na.internal` | 단일 Redis 서버 주소 |
| 포트 | `REDIS_PORT` | `6379` | Redis 서비스 포트 |
| TLS 사용 여부 | `REDIS_SSL` | `true` or `false` | TLS(=rediss) 연결 여부 |

> 로컬 개발 기본값은 `localhost:6379`, `REDIS_SSL=false` 입니다.

## 2. Sentinel 모드
Sentinel이 자동으로 Master를 감시/전환하므로, Master 이름과 Sentinel 노드 리스트를 반드시 채워야 합니다.

| 항목 | 환경 변수 | 예시 값 | 설명 |
| --- | --- | --- | --- |
| Sentinel Master 명 | `REDIS_SENTINEL_MASTER` | `mymaster` | Sentinel 설정의 master 이름 (`sentinel.conf` 의 `sentinel monitor` 이름) |
| Sentinel 노드 | `REDIS_SENTINEL_NODES` | `redis-sentinel-0.na:26379,redis-sentinel-1.na:26379,redis-sentinel-2.na:26379` | 콤마 구분 host:port 목록 |
| TLS 사용 여부 | `REDIS_SSL` | `true` or `false` | Sentinel 연결 시에도 동일하게 적용 |

### 체크 포인트
- Sentinel 노드는 최소 3개 권장 (쿼럼 확보).
- Master/Replica 노드가 TLS를 사용하면 Sentinel도 TLS를 사용해야 하며, 애플리케이션에서는 `REDIS_SSL=true` 만 설정하면 됩니다.

## 3. Cluster 모드
노드별 파티션(샤드)을 제공하는 Redis Cluster 구성입니다. Cluster 값이 존재하면 Sentinel 설정보다 우선 적용됩니다.

| 항목 | 환경 변수 | 예시 값 | 설명 |
| --- | --- | --- | --- |
| Cluster 노드 | `REDIS_CLUSTER_NODES` | `redis-cluster-0.na:6379,redis-cluster-1.na:6379,redis-cluster-2.na:6379` | 마스터 노드 host:port 목록 |
| 최대 redirect 횟수 | `REDIS_CLUSTER_MAX_REDIRECTS` | `5` | MOVED/ASK 응답 시 허용할 재시도 횟수 |
| TLS 사용 여부 | `REDIS_SSL` | `true` or `false` | Cluster 노드 간 TLS 여부 |

### 체크 포인트
- 최소 3개 마스터(각각 replica 포함) 권장.
- 노드 주소는 `host:port` 형태로만 입력하며, 프로토콜은 애플리케이션이 `REDIS_SSL` 값으로 자동 결정.

## 4. 공통 옵션
| 항목 | 환경 변수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| 연결 타임아웃(ms) | `REDIS_TIMEOUT` | `5000` | Redis 커넥션 타임아웃 |
| 풀 최대 커넥션 | `REDIS_POOL_MAX_ACTIVE` | `100` | Spring Lettuce 커넥션 풀 max-active |
| 풀 최대 유휴 | `REDIS_POOL_MAX_IDLE` | `20` | max-idle |
| 풀 최소 유휴 | `REDIS_POOL_MIN_IDLE` | `5` | min-idle |
| 풀 max-wait(ms) | `REDIS_POOL_MAX_WAIT` | `3000` | 풀 대기시간 |

위 값들은 `application.properties` 의 `spring.data.redis.*` 에 자동 바인딩되며, Socket.IO/Redisson 설정도 동일한 정보를 사용합니다.

## 5. 구성 단계 요약
1. **Redis 인프라 구축**  
   - Sentinel: master 1 + replica ≥2 + Sentinel 3노드 권장  
   - Cluster: 최소 3개 master(각 replica 포함) 구성
2. **환경 변수 주입**  
   - 배포 시 `REDIS_SENTINEL_*` 또는 `REDIS_CLUSTER_*`를 설정  
   - TLS 사용 시 `REDIS_SSL=true`
3. **확인**  
   - 애플리케이션 로그에서 `Socket.IO store factory configured to use Redis-backed adapter`와 Sentinel/Cluster 접속 로그 확인  
   - Redis Failover 또는 노드 증설 시 애플리케이션 재시작 없이 자동 반영

## 6. 운영 팁
- Pub/Sub 전용 Redis를 따로 둘 경우 Socket 서버가 바라보는 `REDIS_*` 세트를 pub/sub 인스턴스로 지정하고, 일반 캐시는 별도 RedisTemplate(bean)을 구성합니다.
- TLS 사용 시 모든 노드 주소는 `host:port` 만 입력하고, 애플리케이션엔 `REDIS_SSL=true`를 제공하면 `rediss://` 로 자동 처리됩니다.
- Sentinel/Cluster 구성을 테스트하려면 로컬 docker-compose로 Sentinel/Cluster 환경을 띄운 후 해당 환경 변수를 주입해 부팅 여부를 확인합니다.
