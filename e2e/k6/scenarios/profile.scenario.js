/**
 * 프로필 시나리오
 * - 프로필 조회
 * - 프로필 수정
 */

import { check } from 'k6';
import { config, customConfig } from '../config.js';
import { generateTestUser, register, login } from '../utils/auth.js';
import { getProfile, updateProfile } from '../utils/profile.js';
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
  
  // 2. 프로필 조회
  const profileResult = getProfile(token);
  
  check(profileResult, {
    '프로필 조회 성공': (r) => r.success === true,
    '프로필 데이터 있음': (r) => r.profile !== undefined,
  });
  
  randomSleep(0.5, 1);
  
  // 3. 프로필 수정
  const newName = `Updated User ${Date.now()}`;
  const updateResult = updateProfile(token, { name: newName });
  
  check(updateResult, {
    '프로필 수정 성공': (r) => r === true,
  });
  
  randomSleep(0.5, 1);
  
  // 4. 수정된 프로필 확인
  const updatedProfileResult = getProfile(token);
  
  check(updatedProfileResult, {
    '수정된 프로필 조회 성공': (r) => r.success === true,
    '이름이 변경됨': (r) => r.profile?.name === newName,
  });
}

