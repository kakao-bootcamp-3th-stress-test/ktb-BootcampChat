/**
 * 채팅 관련 유틸리티 함수
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, customConfig } from '../config.js';

/**
 * 채팅방 목록 조회
 * @param {string} token - JWT 토큰
 * @returns {Array} - 채팅방 목록
 */
export function getRooms(token) {
  const url = `${config.baseUrl}/api/rooms`;
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.get(url, params);
  
  const success = check(response, {
    '채팅방 목록 조회 성공': (r) => r.status === 200,
  });
  
  if (response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      return body.data?.rooms || [];
    } catch (e) {
      return [];
    }
  }
  
  return [];
}

/**
 * 채팅방 생성
 * @param {string} token - JWT 토큰
 * @param {string} roomName - 채팅방 이름
 * @returns {Object} - { success: boolean, roomId?: string }
 */
export function createRoom(token, roomName) {
  const url = `${config.baseUrl}/api/rooms`;
  const payload = JSON.stringify({
    name: roomName,
  });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.post(url, payload, params);
  
  const success = check(response, {
    '채팅방 생성 성공': (r) => r.status === 200 || r.status === 201,
  });
  
  if (success && (response.status === 200 || response.status === 201)) {
    try {
      const body = JSON.parse(response.body);
      return {
        success: true,
        roomId: body.data?.room?.id || body.data?.id,
        room: body.data?.room || body.data,
      };
    } catch (e) {
      return { success: false, error: 'JSON 파싱 실패' };
    }
  }
  
  return { success: false, error: `HTTP ${response.status}` };
}

/**
 * 채팅방 참여
 * @param {string} token - JWT 토큰
 * @param {string} roomId - 채팅방 ID
 * @returns {boolean} - 성공 여부
 */
export function joinRoom(token, roomId) {
  const url = `${config.baseUrl}/api/rooms/${roomId}/join`;
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.post(url, null, params);
  
  return check(response, {
    '채팅방 참여 성공': (r) => r.status === 200 || r.status === 201,
  });
}

/**
 * 메시지 전송
 * @param {string} token - JWT 토큰
 * @param {string} roomId - 채팅방 ID
 * @param {string} content - 메시지 내용
 * @param {string} type - 메시지 타입 (TEXT, IMAGE, FILE 등)
 * @returns {Object} - { success: boolean, messageId?: string }
 */
export function sendMessage(token, roomId, content, type = 'TEXT') {
  const url = `${config.baseUrl}/api/message`;
  const payload = JSON.stringify({
    roomId,
    content,
    type,
  });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.post(url, payload, params);
  
  const success = check(response, {
    '메시지 전송 성공': (r) => r.status === 200 || r.status === 201,
    '응답 시간 < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (success && (response.status === 200 || response.status === 201)) {
    try {
      const body = JSON.parse(response.body);
      return {
        success: true,
        messageId: body.data?.message?.id || body.data?.id,
      };
    } catch (e) {
      return { success: false, error: 'JSON 파싱 실패' };
    }
  }
  
  return { success: false, error: `HTTP ${response.status}` };
}

/**
 * 대량 메시지 전송
 * @param {string} token - JWT 토큰
 * @param {string} roomId - 채팅방 ID
 * @param {number} count - 전송할 메시지 개수
 * @returns {number} - 성공한 메시지 개수
 */
export function sendMassMessages(token, roomId, count) {
  let successCount = 0;
  
  for (let i = 0; i < count; i++) {
    const content = `부하 테스트 메시지 ${i + 1} - ${Date.now()}`;
    const result = sendMessage(token, roomId, content);
    
    if (result.success) {
      successCount++;
    }
    
    // 메시지 간 짧은 대기
    sleep(0.1);
  }
  
  return successCount;
}

/**
 * 금칙어 포함 메시지 전송 (필터링 테스트)
 * @param {string} token - JWT 토큰
 * @param {string} roomId - 채팅방 ID
 * @param {Array} forbiddenWords - 금칙어 목록
 * @returns {Object} - { success: boolean, filtered: boolean }
 */
export function sendForbiddenMessage(token, roomId, forbiddenWords) {
  const forbiddenWord = forbiddenWords[Math.floor(Math.random() * forbiddenWords.length)];
  const content = `테스트 메시지 ${forbiddenWord} 포함`;
  
  const result = sendMessage(token, roomId, content);
  
  // 금칙어가 필터링되면 메시지 전송이 실패하거나 특정 응답을 받을 것으로 예상
  return {
    success: result.success,
    filtered: !result.success, // 실패하면 필터링된 것으로 간주
  };
}

