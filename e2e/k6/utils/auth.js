/**
 * 인증 관련 유틸리티 함수
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config } from '../config.js';

/**
 * 고유한 테스트 사용자 데이터 생성
 */
export function generateTestUser() {
  const timestamp = Date.now();
  const randomId = Math.random().toString(36).substring(2, 8);
  
  return {
    email: `loadtest_${timestamp}_${randomId}@example.com`,
    password: 'Password123!',
    passwordConfirm: 'Password123!',
    name: `Load Test User ${randomId}`,
  };
}

/**
 * 회원가입
 * @param {Object} userData - 사용자 데이터
 * @returns {Object} - { success: boolean, userId?: string, token?: string }
 */
export function register(userData) {
  const url = `${config.baseUrl}/api/auth/register`;
  const payload = JSON.stringify({
    email: userData.email,
    password: userData.password,
    passwordConfirm: userData.passwordConfirm,
    name: userData.name,
  });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };
  
  const response = http.post(url, payload, params);
  
  const success = check(response, {
    '회원가입 성공': (r) => r.status === 200 || r.status === 201,
    '응답 시간 < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (success && response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      return {
        success: true,
        userId: body.data?.user?.id,
        token: body.data?.token,
      };
    } catch (e) {
      return { success: false, error: 'JSON 파싱 실패' };
    }
  }
  
  return { success: false, error: `HTTP ${response.status}` };
}

/**
 * 로그인
 * @param {string} email - 이메일
 * @param {string} password - 비밀번호
 * @returns {Object} - { success: boolean, token?: string, sessionId?: string }
 */
export function login(email, password) {
  const url = `${config.baseUrl}/api/auth/login`;
  const payload = JSON.stringify({
    email,
    password,
  });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };
  
  const response = http.post(url, payload, params);
  
  const success = check(response, {
    '로그인 성공': (r) => r.status === 200,
    '응답 시간 < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (success && response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      return {
        success: true,
        token: body.data?.token,
        sessionId: body.data?.sessionId,
        user: body.data?.user,
      };
    } catch (e) {
      return { success: false, error: 'JSON 파싱 실패' };
    }
  }
  
  return { success: false, error: `HTTP ${response.status}` };
}

/**
 * 로그아웃
 * @param {string} token - JWT 토큰
 * @returns {boolean} - 성공 여부
 */
export function logout(token) {
  const url = `${config.baseUrl}/api/auth/logout`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.post(url, null, params);
  
  return check(response, {
    '로그아웃 성공': (r) => r.status === 200 || r.status === 204,
  });
}

/**
 * 토큰 검증
 * @param {string} token - JWT 토큰
 * @returns {boolean} - 유효 여부
 */
export function verifyToken(token) {
  const url = `${config.baseUrl}/api/auth/verify-token`;
  const payload = JSON.stringify({ token });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };
  
  const response = http.post(url, payload, params);
  
  return check(response, {
    '토큰 유효': (r) => r.status === 200,
  });
}

