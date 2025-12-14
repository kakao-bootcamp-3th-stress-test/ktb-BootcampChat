# k6 부하 테스트

채팅 애플리케이션의 k6 기반 부하 테스트 코드입니다.

## 개요

k6는 Grafana에서 개발한 오픈소스 부하 테스트 도구로, JavaScript로 테스트 스크립트를 작성할 수 있습니다. HTTP 기반 API를 직접 호출하여 부하를 생성합니다.

## 설치

### macOS

```bash
# Homebrew를 사용한 설치
brew install k6

# 또는 Makefile 사용
make install
```

### Linux

```bash
# Debian/Ubuntu
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D9
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Windows

```powershell
# Chocolatey 사용
choco install k6

# 또는 Scoop 사용
scoop install k6
```

자세한 설치 방법: https://k6.io/docs/getting-started/installation/

## 환경 검증

```bash
make verify-env
```

## 실행 방법

### Makefile 사용 (권장)

```bash
# 환경 검증
make verify-env

# 전체 시나리오 실행
make test

# 특정 시나리오만 실행
make test-auth      # 인증 시나리오
make test-chat      # 채팅 시나리오
make test-profile   # 프로필 시나리오

# 스트레스 테스트 (서버 한계 찾기)
make test-stress    # 점진적으로 부하 증가 (5명 → 1000명)

# 부하 테스트 (일정한 부하)
make test-load      # 일정한 부하 유지
VUS=100 make test-load  # 100명으로 부하 테스트
```

### 직접 실행

```bash
# 전체 시나리오
k6 run main.js

# 특정 시나리오
k6 run scenarios/auth.scenario.js
k6 run scenarios/chat.scenario.js
k6 run scenarios/profile.scenario.js
```

## 환경 변수

### 기본 설정

- **BASE_URL**: 테스트 대상 URL
  - 기본값: `https://chat.goorm-ktb-002.goorm.team`

- **VUS**: 동시 가상 사용자 수
  - 기본값: `10`

- **DURATION**: 테스트 지속 시간
  - 기본값: `2m` (2분)

### 부하 설정

- **STAGES**: 커스텀 스테이지 설정 (JSON 형식)
  - 기본값: (없음, VUS와 DURATION 사용)
  - 예시: `'[{"duration":"30s","target":10},{"duration":"1m","target":10},{"duration":"30s","target":0}]'`

### 시나리오 설정

- **MASS_MESSAGE_COUNT**: 대량 메시지 전송 개수
  - 기본값: `10`

- **TIMEOUT**: 타임아웃 설정
  - 기본값: `30s`

- **FORBIDDEN_WORDS**: 금칙어 목록 (쉼표로 구분)
  - 기본값: `"b3sig78jv,9c0hej6x,lbl276sz"`

### 사용 예시

```bash
# 20명의 유저로 5분간 테스트
VUS=20 DURATION=5m make test

# 다른 서버로 테스트
BASE_URL=https://example.com make test

# 커스텀 스테이지 사용
STAGES='[{"duration":"1m","target":5},{"duration":"2m","target":10},{"duration":"1m","target":0}]' make test

# 대량 메시지 50개로 테스트
MASS_MESSAGE_COUNT=50 make test

# 타임아웃 증가
TIMEOUT=60s make test
```

## 디렉토리 구조

```
k6/
├── scenarios/              # 부하 테스트 시나리오
│   ├── auth.scenario.js    # 인증 시나리오
│   ├── chat.scenario.js    # 채팅 시나리오
│   └── profile.scenario.js # 프로필 시나리오
├── utils/                  # 유틸리티 함수
│   ├── auth.js             # 인증 관련 함수
│   ├── chat.js             # 채팅 관련 함수
│   ├── profile.js          # 프로필 관련 함수
│   └── helpers.js          # 공통 헬퍼 함수
├── config.js               # 설정 파일
├── main.js                 # 통합 실행 파일
├── Makefile                # 빌드 및 실행 명령어
├── package.json
└── README.md
```

## 시나리오 설명

### 인증 시나리오 (auth.scenario.js)

1. **회원가입**: 고유한 테스트 사용자 생성
2. **로그인**: 생성한 사용자로 로그인
3. **토큰 검증**: JWT 토큰 유효성 확인
4. **로그아웃**: 세션 종료

### 채팅 시나리오 (chat.scenario.js)

1. **회원가입 및 로그인**: 인증 완료
2. **채팅방 목록 조회**: 사용 가능한 채팅방 목록 가져오기
3. **채팅방 생성**: 새 채팅방 생성
4. **채팅방 참여**: 생성한 채팅방에 참여
5. **메시지 전송**: 일반 메시지 전송
6. **대량 메시지 전송**: 여러 메시지 연속 전송 (처리량 테스트)
7. **금칙어 필터링**: 금칙어 포함 메시지 전송 (필터링 동작 확인)

### 프로필 시나리오 (profile.scenario.js)

1. **회원가입 및 로그인**: 인증 완료
2. **프로필 조회**: 현재 사용자 프로필 정보 가져오기
3. **프로필 수정**: 사용자 이름 변경
4. **수정 확인**: 변경된 프로필 정보 확인

### 통합 시나리오 (main.js)

모든 시나리오를 순차적으로 실행합니다:
1. 인증 시나리오 (회원가입, 로그인)
2. 채팅 시나리오 (방 생성, 메시지 전송 등)
3. 프로필 시나리오 (조회, 수정)
4. 로그아웃

## 성능 임계값

기본적으로 다음 임계값이 설정되어 있습니다:

- **HTTP 요청 실패율**: 5% 미만 (`http_req_failed: ['rate<0.05']`)
- **HTTP 응답 시간 (p95)**: 500ms 미만 (`http_req_duration: ['p(95)<500']`)
- **체크 성공률**: 95% 이상 (`checks: ['rate>0.95']`)

테스트 결과에서 이 임계값을 초과하면 테스트가 실패로 표시됩니다.

## 부하 테스트 단계 (Stages)

기본 설정은 다음 단계로 구성됩니다:

1. **램프업**: 30초 동안 0명에서 10명으로 증가
2. **유지**: 1분 동안 10명 유지
3. **램프다운**: 30초 동안 10명에서 0명으로 감소

커스텀 스테이지는 `STAGES` 환경 변수로 설정할 수 있습니다.

## 출력 결과

k6는 테스트 실행 후 다음과 같은 정보를 제공합니다:

- **요약 통계**: 총 요청 수, 성공/실패율, 평균 응답 시간
- **그룹별 통계**: 시나리오별 성능 지표
- **임계값 결과**: 설정한 임계값 달성 여부
- **HTTP 통계**: 상태 코드별 분포, 응답 시간 분포

## 주의사항

1. **서버 준비**: 부하 테스트 전에 대상 서버가 준비되었는지 확인하세요.
2. **데이터 생성**: 각 테스트마다 고유한 사용자를 생성하므로 DB가 증가합니다.
3. **리소스 사용**: 높은 부하는 서버와 클라이언트 모두에 부하를 줍니다.
4. **네트워크**: 네트워크 환경에 따라 타임아웃 조정이 필요할 수 있습니다.
5. **테스트 데이터**: 주기적인 데이터 정리를 계획하세요.

## 트러블슈팅

### k6가 설치되지 않았다는 오류

```bash
make verify-env
# 또는
make install
```

### 타임아웃 오류가 자주 발생

```bash
# 타임아웃 증가
TIMEOUT=60s make test
```

### 메모리 부족 문제

```bash
# 동시 유저 수 감소
VUS=5 DURATION=1m make test
```

### HTTP 401 오류 (인증 실패)

- 토큰이 제대로 전달되는지 확인
- 서버의 인증 설정 확인
- BASE_URL이 올바른지 확인

## k6 vs Artillery 비교

| 특징 | k6 | Artillery |
|------|-----|-----------|
| 엔진 | HTTP 클라이언트 | Playwright (브라우저) |
| 리소스 사용 | 낮음 | 높음 (브라우저 인스턴스) |
| API 테스트 | 적합 | 부적합 |
| E2E 테스트 | 부적합 | 적합 |
| 성능 | 빠름 | 느림 |
| 동시 사용자 | 수천 명 가능 | 수십 명 제한 |

k6는 **API 레벨 부하 테스트**에 최적화되어 있고, Artillery는 **브라우저 기반 E2E 부하 테스트**에 적합합니다.

## 스트레스 테스트

서버가 얼마나 많은 부하를 견딜 수 있는지 확인하려면 스트레스 테스트를 실행하세요.

```bash
# 점진적으로 부하를 증가시켜 서버 한계 찾기
make test-stress
```

이 테스트는 5명에서 시작해서 1000명까지 점진적으로 부하를 증가시킵니다.

자세한 내용은 [STRESS_TEST.md](./STRESS_TEST.md)를 참고하세요.

## 관련 문서

- [k6 공식 문서](https://k6.io/docs/)
- [k6 JavaScript API](https://k6.io/docs/javascript-api/)
- [k6 임계값 설정](https://k6.io/docs/using-k6/thresholds/)
- [k6 스테이지 설정](https://k6.io/docs/using-k6/scenarios/executors/ramping-vus/)
- [스트레스 테스트 가이드](./STRESS_TEST.md)

