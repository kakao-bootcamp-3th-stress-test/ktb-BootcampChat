/**
 * 부하 테스트 시나리오 (일반 부하)
 * 일정한 부하를 유지하여 서버의 안정성을 확인합니다.
 */

import { check } from 'k6';
import { config, customConfig } from '../config.js';
import { generateTestUser, register, login } from '../utils/auth.js';
import {
  getRooms,
  createRoom,
  joinRoom,
  sendMessage,
} from '../utils/chat.js';
import { getProfile } from '../utils/profile.js';
import { randomSleep } from '../utils/helpers.js';

/**
 * 부하 테스트 옵션
 * 일정한 부하를 유지합니다.
 */
export const options = {
  stages: [
    // 램프업: 30초 동안 0명에서 목표 인원으로 증가
    { duration: '30s', target: parseInt(__ENV.VUS || '50') },
    // 유지: 5분 동안 일정한 부하 유지
    { duration: '5m', target: parseInt(__ENV.VUS || '50') },
    // 램프다운: 30초 동안 0명으로 감소
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  const testUser = generateTestUser();
  const registerResult = register(testUser);
  
  check(registerResult, {
    '회원가입 성공': (r) => r.success === true,
  });
  
  if (!registerResult.success) {
    return;
  }
  
  randomSleep(0.5, 1);
  
  const loginResult = login(testUser.email, testUser.password);
  
  check(loginResult, {
    '로그인 성공': (r) => r.success === true,
  });
  
  if (!loginResult.success || !loginResult.token) {
    return;
  }
  
  const token = loginResult.token;
  randomSleep(0.5, 1);
  
  // 채팅방 목록 조회
  const rooms = getRooms(token);
  check(rooms, {
    '채팅방 목록 조회 성공': (r) => Array.isArray(r),
  });
  
  randomSleep(0.5, 1);
  
  // 채팅방 생성
  const roomName = `부하테스트_${Date.now()}`;
  const createResult = createRoom(token, roomName);
  
  check(createResult, {
    '채팅방 생성 성공': (r) => r.success === true,
  });
  
  if (!createResult.success || !createResult.roomId) {
    return;
  }
  
  const roomId = createResult.roomId;
  randomSleep(0.5, 1);
  
  // 채팅방 참여
  const joinResult = joinRoom(token, roomId);
  check(joinResult, {
    '채팅방 참여 성공': (r) => r === true,
  });
  
  randomSleep(0.5, 1);
  
  // 메시지 전송
  const messageContent = `부하 테스트 메시지 ${Date.now()}`;
  const sendResult = sendMessage(token, roomId, messageContent);
  
  check(sendResult, {
    '메시지 전송 성공': (r) => r.success === true,
  });
  
  randomSleep(0.5, 1);
  
  // 프로필 조회
  const profileResult = getProfile(token);
  check(profileResult, {
    '프로필 조회 성공': (r) => r.success === true,
  });
}

