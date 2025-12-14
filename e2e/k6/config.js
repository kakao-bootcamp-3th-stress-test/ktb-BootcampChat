/**
 * k6 부하 테스트 설정
 */

// 기본 설정값
export const config = {
  // 테스트 대상 서버 URL
  baseUrl: __ENV.BASE_URL || 'https://chat.goorm-ktb-002.goorm.team',
  
  // 부하 테스트 설정
  stages: [
    // 램프업: 30초 동안 0명에서 10명으로 증가
    { duration: '30s', target: 10 },
    // 유지: 1분 동안 10명 유지
    { duration: '1m', target: 10 },
    // 램프다운: 30초 동안 10명에서 0명으로 감소
    { duration: '30s', target: 0 },
  ],
  
  // 임계값 설정 (성능 목표)
  thresholds: {
    // HTTP 요청 성공률 95% 이상
    http_req_failed: ['rate<0.05'],
    // HTTP 요청 응답 시간 (p95가 500ms 이하)
    http_req_duration: ['p(95)<500'],
    // 체크 성공률 95% 이상
    checks: ['rate>0.95'],
  },
};

// 커스텀 설정 (환경 변수로 오버라이드 가능)
export const customConfig = {
  // 동시 사용자 수 (간단한 설정용)
  vus: parseInt(__ENV.VUS || '10'),
  duration: __ENV.DURATION || '2m',
  
  // 부하 테스트 단계 (환경 변수로 오버라이드 가능)
  // STAGES가 설정되어 있으면 사용, 없으면 VUS와 DURATION으로 stages 생성
  stages: __ENV.STAGES 
    ? JSON.parse(__ENV.STAGES) 
    : (__ENV.VUS || __ENV.DURATION
      ? [
          { duration: '30s', target: parseInt(__ENV.VUS || '10') },
          { duration: __ENV.DURATION || '2m', target: parseInt(__ENV.VUS || '10') },
          { duration: '30s', target: 0 },
        ]
      : config.stages),
  
  // 테스트 데이터 설정
  massMessageCount: parseInt(__ENV.MASS_MESSAGE_COUNT || '10'),
  
  // 타임아웃 설정
  timeout: __ENV.TIMEOUT || '30s',
  
  // 금칙어 목록
  forbiddenWords: __ENV.FORBIDDEN_WORDS ? __ENV.FORBIDDEN_WORDS.split(',') : ['b3sig78jv', '9c0hej6x', 'lbl276sz'],
};

