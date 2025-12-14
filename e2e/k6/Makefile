###################################
# Environment Variables
###################################
# k6가 부하를 주는 대상 URL
export BASE_URL ?= https://chat.goorm-ktb-002.goorm.team

# 부하 테스트 설정
export VUS ?= 10              # 동시 가상 사용자 수
export DURATION ?= 2m          # 테스트 지속 시간

# 커스텀 스테이지 설정 (JSON 형식)
# 예: '[{"duration":"30s","target":10},{"duration":"1m","target":10},{"duration":"30s","target":0}]'
export STAGES ?=

# 대량 채팅발송 시, 발송되는 메시지 갯수
export MASS_MESSAGE_COUNT ?= 10

# 타임아웃 설정 (초 단위)
export TIMEOUT ?= 30s

# 금칙어 목록 (쉼표로 구분)
export FORBIDDEN_WORDS ?= "b3sig78jv,9c0hej6x,lbl276sz"

###################################
# Commands
###################################

.PHONY: help verify-env install test test-auth test-chat test-profile test-all test-stress test-load

help:
	@echo "k6 부하 테스트 명령어:"
	@echo "  make verify-env     - 환경 검증 (k6 설치 확인)"
	@echo "  make install         - k6 설치 (macOS)"
	@echo "  make test           - 전체 시나리오 실행"
	@echo "  make test-auth      - 인증 시나리오만 실행"
	@echo "  make test-chat      - 채팅 시나리오만 실행"
	@echo "  make test-profile   - 프로필 시나리오만 실행"
	@echo "  make test-stress    - 스트레스 테스트 (점진적 부하 증가, 한계 찾기)"
	@echo "  make test-load      - 부하 테스트 (일정한 부하 유지)"
	@echo ""
	@echo "환경 변수 예시:"
	@echo "  VUS=20 DURATION=5m make test"
	@echo "  BASE_URL=https://example.com make test"
	@echo "  VUS=100 make test-load    # 100명으로 부하 테스트"
	@echo "  DISABLE_THRESHOLDS=true make test-stress  # 임계값 검사 비활성화"

verify-env:
	@echo "🔍 환경 검증 중..."
	@command -v k6 >/dev/null 2>&1 || { \
		echo "❌ k6가 설치되지 않았습니다."; \
		echo "   설치 방법: make install (macOS)"; \
		echo "   또는 https://k6.io/docs/getting-started/installation/ 참고"; \
		exit 1; \
	}
	@echo "✅ k6 설치 확인됨: $$(k6 version | head -n 1)"
	@command -v node >/dev/null 2>&1 || { \
		echo "❌ Node.js가 설치되지 않았습니다."; \
		exit 1; \
	}
	@echo "✅ Node.js 설치 확인됨: $$(node --version)"

install:
	@echo "📦 k6 설치 중 (macOS)..."
	@command -v brew >/dev/null 2>&1 || { \
		echo "❌ Homebrew가 설치되지 않았습니다."; \
		echo "   https://brew.sh 에서 Homebrew를 설치하세요."; \
		exit 1; \
	}
	@brew install k6 || { \
		echo "❌ k6 설치 실패"; \
		exit 1; \
	}
	@echo "✅ k6 설치 완료"

test:
	@echo "🚀 전체 시나리오 부하 테스트 실행 중..."
	@echo "   대상: $(BASE_URL)"
	@echo "   VUs: $(VUS), Duration: $(DURATION)"
	@k6 run main.js

test-auth:
	@echo "🚀 인증 시나리오 부하 테스트 실행 중..."
	@k6 run scenarios/auth.scenario.js

test-chat:
	@echo "🚀 채팅 시나리오 부하 테스트 실행 중..."
	@k6 run scenarios/chat.scenario.js

test-profile:
	@echo "🚀 프로필 시나리오 부하 테스트 실행 중..."
	@k6 run scenarios/profile.scenario.js

test-all:
	@echo "🚀 전체 시나리오 부하 테스트 실행 중..."
	@k6 run main.js

test-stress:
	@echo "🔥 스트레스 테스트 실행 중 (서버 한계 찾기)..."
	@echo "   대상: $(BASE_URL)"
	@echo "   부하: 5명 → 10명 → 20명 → 50명 → 100명 → 200명 → 500명 → 1000명 → 2000명 → 3000명"
	@echo "   ⚠️  주의: 서버에 매우 높은 부하를 줍니다!"
	@echo "   💡 팁: 임계값 에러가 발생하면 DISABLE_THRESHOLDS=true make test-stress 로 실행하세요"
	@k6 run scenarios/stress.scenario.js || true

test-load:
	@echo "⚡ 부하 테스트 실행 중 (일정한 부하 유지)..."
	@echo "   대상: $(BASE_URL)"
	@echo "   VUs: $(VUS), Duration: $(DURATION)"
	@k6 run scenarios/load.scenario.js

