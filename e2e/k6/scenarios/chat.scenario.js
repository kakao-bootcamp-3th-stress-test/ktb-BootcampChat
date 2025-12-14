/**
 * 채팅 시나리오
 * - 채팅방 목록 조회
 * - 채팅방 생성
 * - 채팅방 참여
 * - 메시지 전송
 * - 대량 메시지 전송
 * - 금칙어 필터링 테스트
 */

import { check } from 'k6';
import { config, customConfig } from '../config.js';
import { generateTestUser, register, login } from '../utils/auth.js';
import {
  getRooms,
  createRoom,
  joinRoom,
  sendMessage,
  sendMassMessages,
  sendForbiddenMessage,
} from '../utils/chat.js';
import { randomSleep, randomChoice } from '../utils/helpers.js';

export const options = {
  stages: customConfig.stages,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  // 1. 회원가입 및 로그인
  const testUser = generateTestUser();
  const registerResult = register(testUser);
  
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
  
  // 2. 채팅방 목록 조회
  const rooms = getRooms(token);
  
  check(rooms, {
    '채팅방 목록 조회 성공': (r) => Array.isArray(r),
  });
  
  randomSleep(0.5, 1);
  
  // 3. 채팅방 생성
  const roomName = `부하테스트 방 ${Date.now()}`;
  const createResult = createRoom(token, roomName);
  
  check(createResult, {
    '채팅방 생성 성공': (r) => r.success === true,
    '채팅방 ID 받음': (r) => r.roomId !== undefined,
  });
  
  if (!createResult.success || !createResult.roomId) {
    return;
  }
  
  const roomId = createResult.roomId;
  randomSleep(0.5, 1);
  
  // 4. 채팅방 참여
  const joinResult = joinRoom(token, roomId);
  
  check(joinResult, {
    '채팅방 참여 성공': (r) => r === true,
  });
  
  randomSleep(0.5, 1);
  
  // 5. 일반 메시지 전송
  const messageContent = `부하 테스트 메시지 ${Date.now()}`;
  const sendResult = sendMessage(token, roomId, messageContent);
  
  check(sendResult, {
    '메시지 전송 성공': (r) => r.success === true,
  });
  
  randomSleep(0.5, 1);
  
  // 6. 대량 메시지 전송
  const massCount = customConfig.massMessageCount;
  const successCount = sendMassMessages(token, roomId, massCount);
  
  check(successCount, {
    '대량 메시지 전송 성공률': (c) => c >= massCount * 0.9, // 90% 이상 성공
  });
  
  randomSleep(1, 2);
  
  // 7. 금칙어 필터링 테스트
  const forbiddenResult = sendForbiddenMessage(
    token,
    roomId,
    customConfig.forbiddenWords
  );
  
  check(forbiddenResult, {
    '금칙어 필터링 동작': (r) => r.filtered === true || r.success === false,
  });
}

