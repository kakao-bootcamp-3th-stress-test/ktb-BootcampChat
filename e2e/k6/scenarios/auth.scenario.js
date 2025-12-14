/**
 * 인증 시나리오
 * - 회원가입
 * - 로그인
 * - 로그아웃
 * - 토큰 검증
 */

import { check } from 'k6';
import { config, customConfig } from '../config.js';
import { generateTestUser, register, login, logout, verifyToken } from '../utils/auth.js';
import { randomSleep } from '../utils/helpers.js';

export const options = {
  stages: customConfig.stages,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  // 1. 회원가입
  const testUser = generateTestUser();
  const registerResult = register(testUser);
  
  check(registerResult, {
    '회원가입 성공': (r) => r.success === true,
  });
  
  randomSleep(0.5, 1);
  
  // 2. 로그인
  const loginResult = login(testUser.email, testUser.password);
  
  check(loginResult, {
    '로그인 성공': (r) => r.success === true,
    '토큰 받음': (r) => r.token !== undefined && r.token !== null,
  });
  
  if (!loginResult.success || !loginResult.token) {
    return; // 로그인 실패 시 종료
  }
  
  randomSleep(0.5, 1);
  
  // 3. 토큰 검증
  const verifyResult = verifyToken(loginResult.token);
  
  check(verifyResult, {
    '토큰 검증 성공': (r) => r === true,
  });
  
  randomSleep(0.5, 1);
  
  // 4. 로그아웃
  const logoutResult = logout(loginResult.token);
  
  check(logoutResult, {
    '로그아웃 성공': (r) => r === true,
  });
}

