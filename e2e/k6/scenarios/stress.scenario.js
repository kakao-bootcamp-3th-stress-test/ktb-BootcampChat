/**
 * 스트레스 테스트 시나리오
 * 점진적으로 부하를 증가시켜 서버의 한계를 찾습니다.
 */

import { check } from 'k6';
import { config, customConfig } from '../config.js';
import { generateTestUser, register, login, logout } from '../utils/auth.js';
import {
  getRooms,
  createRoom,
  joinRoom,
  sendMessage,
  sendMassMessages,
} from '../utils/chat.js';
import { getProfile, updateProfile } from '../utils/profile.js';
import { randomSleep } from '../utils/helpers.js';

/**
 * 스트레스 테스트 옵션
 * 점진적으로 부하를 증가시켜 서버의 한계를 찾습니다.
 */
export const options = {
  stages: [
    // 1단계: 1분 동안 5명으로 시작
    { duration: '1m', target: 5 },
    // 2단계: 1분 동안 10명으로 증가
    { duration: '1m', target: 10 },
    // 3단계: 1분 동안 20명으로 증가
    { duration: '1m', target: 20 },
    // 4단계: 1분 동안 50명으로 증가
    { duration: '1m', target: 50 },
    // 5단계: 1분 동안 100명으로 증가
    { duration: '1m', target: 100 },
    // 6단계: 1분 동안 200명으로 증가
    { duration: '1m', target: 200 },
    // 7단계: 1분 동안 500명으로 증가
    { duration: '1m', target: 500 },
    // 8단계: 2분 동안 500명 유지 (최대 부하 유지)
    { duration: '2m', target: 500 },
    // 9단계: 1분 동안 1000명으로 급증 (극한 테스트)
    { duration: '1m', target: 1000 },
    // 10단계: 1분 동안 1000명 유지
    { duration: '1m', target: 1000 },
    // 11단계: 1분 동안 2000명으로 급증
    { duration: '1m', target: 2000 },
    // 12단계: 2분 동안 2000명 유지
    { duration: '2m', target: 2000 },
    // 13단계: 1분 동안 3000명으로 급증
    { duration: '1m', target: 3000 },
    // 14단계: 2분 동안 3000명 유지
    { duration: '2m', target: 3000 },
    // 15단계: 점진적으로 감소
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    // 스트레스 테스트는 서버 한계를 찾는 것이므로 임계값을 매우 관대하게 설정
    // 환경 변수 DISABLE_THRESHOLDS=true로 설정하면 임계값 검사를 완전히 비활성화
    ...(__ENV.DISABLE_THRESHOLDS === 'true' ? {} : {
      // 실패율이 50%를 넘으면 경고 (스트레스 테스트이므로 매우 관대)
      http_req_failed: ['rate<0.50'],
      // 응답 시간이 10초를 넘으면 경고 (스트레스 테스트이므로 매우 관대)
      http_req_duration: ['p(95)<10000', 'p(99)<30000'],
      // 체크 성공률 50% 이상 (스트레스 테스트이므로 매우 관대)
      checks: ['rate>0.50'],
    }),
  },
};

export default function () {
  // ========== 1. 인증 시나리오 ==========
  
  const testUser = generateTestUser();
  const registerResult = register(testUser);
  
  check(registerResult, {
    '회원가입 성공': (r) => r.success === true,
  });
  
  if (!registerResult.success) {
    return;
  }
  
  randomSleep(0.3, 0.8);
  
  const loginResult = login(testUser.email, testUser.password);
  
  check(loginResult, {
    '로그인 성공': (r) => r.success === true,
    '토큰 받음': (r) => r.token !== undefined && r.token !== null,
  });
  
  if (!loginResult.success || !loginResult.token) {
    return;
  }
  
  const token = loginResult.token;
  randomSleep(0.3, 0.8);
  
  // ========== 2. 채팅 시나리오 ==========
  
  // 채팅방 목록 조회
  const rooms = getRooms(token);
  
  check(rooms, {
    '채팅방 목록 조회 성공': (r) => Array.isArray(r),
  });
  
  randomSleep(0.3, 0.8);
  
  // 채팅방 생성
  const roomName = `스트레스테스트_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
  const createResult = createRoom(token, roomName);
  
  check(createResult, {
    '채팅방 생성 성공': (r) => r.success === true,
  });
  
  if (!createResult.success || !createResult.roomId) {
    return;
  }
  
  const roomId = createResult.roomId;
  randomSleep(0.3, 0.8);
  
  // 채팅방 참여
  const joinResult = joinRoom(token, roomId);
  
  check(joinResult, {
    '채팅방 참여 성공': (r) => r === true,
  });
  
  randomSleep(0.3, 0.8);
  
  // 메시지 전송 (스트레스 테스트이므로 빠르게)
  const messageContent = `스트레스 테스트 메시지 ${Date.now()}`;
  const sendResult = sendMessage(token, roomId, messageContent);
  
  check(sendResult, {
    '메시지 전송 성공': (r) => r.success === true,
  });
  
  randomSleep(0.2, 0.5);
  
  // ========== 3. 프로필 시나리오 ==========
  
  const profileResult = getProfile(token);
  
  check(profileResult, {
    '프로필 조회 성공': (r) => r.success === true,
  });
  
  randomSleep(0.2, 0.5);
}

