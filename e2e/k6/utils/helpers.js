/**
 * 공통 헬퍼 함수
 */

import { check, sleep } from 'k6';

/**
 * HTTP 응답 검증
 * @param {Object} response - k6 HTTP 응답 객체
 * @param {string} name - 체크 이름
 * @returns {boolean} - 성공 여부
 */
export function checkResponse(response, name) {
  const checkName1 = name + ' - 상태 코드 200';
  const checkName2 = name + ' - 응답 시간 < 1s';
  return check(response, {
    [checkName1]: (r) => r.status === 200,
    [checkName2]: (r) => r.timings.duration < 1000,
  });
}

/**
 * 랜덤 대기 시간 (지연 시뮬레이션)
 * @param {number} min - 최소 시간 (초)
 * @param {number} max - 최대 시간 (초)
 */
export function randomSleep(min = 0.5, max = 2) {
  const sleepTime = min + Math.random() * (max - min);
  sleep(sleepTime);
}

/**
 * 배열에서 랜덤 요소 선택
 * @param {Array} array - 배열
 * @returns {*} - 랜덤 요소
 */
export function randomChoice(array) {
  if (!array || array.length === 0) {
    return null;
  }
  return array[Math.floor(Math.random() * array.length)];
}

/**
 * 고유 ID 생성
 * @returns {string} - 고유 ID
 */
export function generateUniqueId() {
  return `${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}

