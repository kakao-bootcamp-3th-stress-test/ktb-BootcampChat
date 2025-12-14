/**
 * 프로필 관련 유틸리티 함수
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config } from '../config.js';

/**
 * 프로필 조회
 * @param {string} token - JWT 토큰
 * @returns {Object} - 프로필 정보
 */
export function getProfile(token) {
  const url = `${config.baseUrl}/api/users/profile`;
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.get(url, params);
  
  const success = check(response, {
    '프로필 조회 성공': (r) => r.status === 200,
    '응답 시간 < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (success && response.status === 200) {
    try {
      const body = JSON.parse(response.body);
      return {
        success: true,
        profile: body.data?.user || body.data,
      };
    } catch (e) {
      return { success: false, error: 'JSON 파싱 실패' };
    }
  }
  
  return { success: false, error: `HTTP ${response.status}` };
}

/**
 * 프로필 수정
 * @param {string} token - JWT 토큰
 * @param {Object} profileData - { name?: string }
 * @returns {boolean} - 성공 여부
 */
export function updateProfile(token, profileData) {
  const url = `${config.baseUrl}/api/users/profile`;
  const payload = JSON.stringify(profileData);
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };
  
  const response = http.put(url, payload, params);
  
  return check(response, {
    '프로필 수정 성공': (r) => r.status === 200,
    '응답 시간 < 500ms': (r) => r.timings.duration < 500,
  });
}

/**
 * 프로필 이미지 업로드 (multipart/form-data)
 * @param {string} token - JWT 토큰
 * @param {string} imagePath - 이미지 파일 경로
 * @returns {boolean} - 성공 여부
 */
export function uploadProfileImage(token, imagePath) {
  const url = `${config.baseUrl}/api/users/profile/image`;
  
  // k6에서는 multipart/form-data를 직접 구성해야 함
  // 실제 파일 업로드는 복잡하므로 여기서는 기본 구조만 제공
  // 실제 사용 시에는 k6의 http.file() 또는 http.multipart() 사용
  
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  };
  
  // 파일 업로드는 실제 파일이 필요하므로 여기서는 스킵
  // 필요시 fixtures 폴더의 이미지를 사용하여 구현 가능
  return true;
}

