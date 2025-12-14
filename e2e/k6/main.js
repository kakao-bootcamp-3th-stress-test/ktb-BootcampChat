/**
 * k6 부하 테스트 메인 실행 파일
 * 모든 시나리오를 통합하여 실행
 */

import { check } from 'k6';
import { config, customConfig } from './config.js';
import { generateTestUser, register, login, logout } from './utils/auth.js';
import {
  getRooms,
  createRoom,
  joinRoom,
  sendMessage,
  sendMassMessages,
  sendForbiddenMessage,
} from './utils/chat.js';
import { getProfile, updateProfile } from './utils/profile.js';
import { randomSleep } from './utils/helpers.js';

export const options = {
  stages: customConfig.stages,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.95'],
  },
};

/**
 * 전체 시나리오 통합 실행
 */
export default function () {
  // ========== 1. 인증 시나리오 ==========
  
  // 회원가입
  const testUser = generateTestUser();
  const registerResult = register(testUser);
  
  check(registerResult, {
    '회원가입 성공': (r) => r.success === true,
  });
  
  if (!registerResult.success) {
    return; // 회원가입 실패 시 종료
  }
  
  randomSleep(0.5, 1);
  
  // 로그인
  const loginResult = login(testUser.email, testUser.password);
  
  check(loginResult, {
    '로그인 성공': (r) => r.success === true,
    '토큰 받음': (r) => r.token !== undefined && r.token !== null,
  });
  
  if (!loginResult.success || !loginResult.token) {
    return; // 로그인 실패 시 종료
  }
  
  const token = loginResult.token;
  randomSleep(0.5, 1);
  
  // ========== 2. 채팅 시나리오 ==========
  
  // 채팅방 목록 조회
  const rooms = getRooms(token);
  
  check(rooms, {
    '채팅방 목록 조회 성공': (r) => Array.isArray(r),
  });
  
  randomSleep(0.5, 1);
  
  // 채팅방 생성
  const roomName = `부하테스트 방 ${Date.now()}`;
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
  
  // 대량 메시지 전송
  const massCount = customConfig.massMessageCount;
  const successCount = sendMassMessages(token, roomId, massCount);
  
  check(successCount, {
    '대량 메시지 전송 성공률': (c) => c >= massCount * 0.9,
  });
  
  randomSleep(1, 2);
  
  // 금칙어 필터링 테스트
  const forbiddenResult = sendForbiddenMessage(
    token,
    roomId,
    customConfig.forbiddenWords
  );
  
  check(forbiddenResult, {
    '금칙어 필터링 동작': (r) => r.filtered === true || r.success === false,
  });
  
  randomSleep(0.5, 1);
  
  // ========== 3. 프로필 시나리오 ==========
  
  // 프로필 조회
  const profileResult = getProfile(token);
  
  check(profileResult, {
    '프로필 조회 성공': (r) => r.success === true,
  });
  
  randomSleep(0.5, 1);
  
  // 프로필 수정
  const newName = `Updated User ${Date.now()}`;
  const updateResult = updateProfile(token, { name: newName });
  
  check(updateResult, {
    '프로필 수정 성공': (r) => r === true,
  });
  
  randomSleep(0.5, 1);
  
  // ========== 4. 로그아웃 ==========
  
  const logoutResult = logout(token);
  
  check(logoutResult, {
    '로그아웃 성공': (r) => r === true,
  });
}

