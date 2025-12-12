// 금칙어 검사 유틸리티 (Trie 자료구조 최적화)
// 시간복잡도: O(n*m) → O(m*k) 개선
// n = 금칙어 수(10,000), m = 메시지 길이, k = 평균 금칙어 길이

import { BANNED_WORDS_MAP, FIRST_CHARS, TOTAL_COUNT } from './bannedWords';

/**
 * Trie 노드
 */
class TrieNode {
  constructor() {
    this.children = new Map(); // HashMap으로 빠른 검색
    this.isEnd = false; // 금칙어의 끝인지 표시
  }
}

/**
 * 최적화된 금칙어 검사기
 */
class BannedWordChecker {
  constructor() {
    this.root = new TrieNode();
    this.isLoaded = false;
    this.initializeTrie();
  }

  /**
   * 금칙어를 Trie에 삽입
   */
  insert(word) {
    let node = this.root;
    const lowerWord = word.toLowerCase();

    for (const char of lowerWord) {
      if (!node.children.has(char)) {
        node.children.set(char, new TrieNode());
      }
      node = node.children.get(char);
    }
    node.isEnd = true;
  }

  /**
   * Trie 초기화 (첫 글자 HashMap에서 최적화된 로드)
   */
  initializeTrie() {
    // 첫 글자별로 그룹화된 HashMap을 사용하여 Trie 구축
    for (const words of Object.values(BANNED_WORDS_MAP)) {
      for (const word of words) {
        this.insert(word);
      }
    }

    this.isLoaded = true;
    console.log(`Loaded ${TOTAL_COUNT} banned words from ${FIRST_CHARS.size} first-char groups into Trie structure`);
  }

  /**
   * 메시지에 금칙어가 포함되어 있는지 검사 (Trie 사용)
   * 메시지의 모든 부분 문자열을 Trie에서 검색
   * 시간복잡도: O(m * k) where m = 메시지 길이, k = 평균 금칙어 길이
   */
  containsBannedWord(message) {
    if (!message || message.trim().length === 0) {
      return false;
    }

    if (!this.isLoaded) {
      console.warn('Banned words not loaded yet');
      return false;
    }

    const normalized = message.toLowerCase();

    // 메시지의 각 위치에서 시작하는 부분 문자열 검사
    for (let i = 0; i < normalized.length; i++) {
      const firstChar = normalized[i];

      // 첫 글자가 금칙어의 첫 글자 목록에 없으면 스킵 (최적화)
      if (!FIRST_CHARS.has(firstChar)) {
        continue;
      }

      let node = this.root;

      // 현재 위치부터 끝까지 Trie를 따라가며 검색
      for (let j = i; j < normalized.length; j++) {
        const char = normalized[j];

        // Trie에 해당 문자가 없으면 종료
        if (!node.children.has(char)) {
          break;
        }

        // 다음 노드로 이동
        node = node.children.get(char);

        // 금칙어의 끝을 발견하면 즉시 true 반환
        if (node.isEnd) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * 금칙어 목록이 로드되었는지 확인
   */
  isReady() {
    return this.isLoaded;
  }
}

// 싱글톤 인스턴스
const bannedWordChecker = new BannedWordChecker();

export default bannedWordChecker;