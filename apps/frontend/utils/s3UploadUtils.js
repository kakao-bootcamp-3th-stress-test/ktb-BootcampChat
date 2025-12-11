/**
 * S3 직접 업로드 유틸리티
 * 프론트엔드에서 직접 S3 버킷에 파일을 업로드합니다.
 */

// S3 버킷 설정 (환경 변수에서 읽어옴)
const S3_CONFIG = {
  bucket: process.env.NEXT_PUBLIC_S3_BUCKET || 'ktb-chat-files-1765426659',
  region: process.env.NEXT_PUBLIC_S3_REGION || 'ap-northeast-2',
  baseUrl: process.env.NEXT_PUBLIC_S3_BASE_URL || 'https://ktb-chat-files-1765426659.s3.ap-northeast-2.amazonaws.com',
  cloudFrontUrl: process.env.NEXT_PUBLIC_CLOUDFRONT_URL || 'https://d313lw9tnm33t8.cloudfront.net',
  uploadPaths: {
    files: 'uploads',
    profiles: 'profiles'
  }
};

// 백엔드와 동일한 파일 제한 설정
const FILE_LIMITS = {
  profileImage: {
    maxSize: 5 * 1024 * 1024, // 5MB
    allowedTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
    allowedExtensions: ['jpg', 'jpeg', 'png', 'gif', 'webp']
  },
  file: {
    image: {
      maxSize: 10 * 1024 * 1024, // 10MB
      allowedTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
      allowedExtensions: ['jpg', 'jpeg', 'png', 'gif', 'webp']
    },
    pdf: {
      maxSize: 20 * 1024 * 1024, // 20MB
      allowedTypes: ['application/pdf'],
      allowedExtensions: ['pdf']
    }
  }
};

/**
 * 안전한 파일명 생성 (백엔드 FileUtil과 동일한 로직)
 */
function generateSafeFileName(originalFilename) {
  if (!originalFilename || originalFilename.trim() === '') {
    return generateRandomFileName('file');
  }

  // 파일 확장자 분리
  const extension = getFileExtension(originalFilename);

  // 타임스탬프와 16자리 hex 랜덤 값으로 고유성 보장
  const timestamp = Date.now();
  const randomHex = generateRandomHex(16);

  if (extension) {
    return `${timestamp}_${randomHex}.${extension}`;
  } else {
    return `${timestamp}_${randomHex}`;
  }
}

/**
 * 파일 확장자 추출
 */
function getFileExtension(filename) {
  if (!filename) return '';
  const parts = filename.split('.');
  return parts.length > 1 ? parts.pop().toLowerCase() : '';
}

/**
 * 랜덤 16진수 문자열 생성
 */
function generateRandomHex(length) {
  const bytes = new Uint8Array(length / 2);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * 랜덤 파일명 생성
 */
function generateRandomFileName(prefix) {
  const timestamp = Date.now();
  const random = Math.floor(Math.random() * 10000);
  return `${prefix}_${timestamp}_${random.toString().padStart(4, '0')}`;
}

/**
 * 원본 파일명 정규화 (경로 문자 제거)
 */
function normalizeOriginalFilename(originalFilename) {
  if (!originalFilename || originalFilename.trim() === '') {
    return '';
  }
  
  // 경로 문자 제거 (/, \)
  return originalFilename.replace(/[/\\]/g, '');
}

/**
 * 프로필 이미지 파일 검증 (백엔드와 동일한 로직)
 * 내부 사용 전용
 */
function validateProfileImage(file) {
  if (!file) {
    return { success: false, message: '이미지가 제공되지 않았습니다.' };
  }

  // 파일 크기 검증 (5MB)
  if (file.size > FILE_LIMITS.profileImage.maxSize) {
    return { success: false, message: '파일 크기는 5MB를 초과할 수 없습니다.' };
  }

  // Content-Type 검증
  if (!file.type || !file.type.startsWith('image/')) {
    return { success: false, message: '이미지 파일만 업로드할 수 있습니다.' };
  }

  // MIME 타입 검증
  if (!FILE_LIMITS.profileImage.allowedTypes.includes(file.type)) {
    return { success: false, message: '이미지 파일만 업로드할 수 있습니다.' };
  }

  // 파일 확장자 검증
  const extension = getFileExtension(file.name).toLowerCase();
  if (!FILE_LIMITS.profileImage.allowedExtensions.includes(extension)) {
    return { success: false, message: '이미지 파일만 업로드할 수 있습니다.' };
  }

  // 파일명 길이 검증 (UTF-8 바이트 기준 255바이트)
  const filenameBytes = new TextEncoder().encode(file.name).length;
  if (filenameBytes > 255) {
    return { success: false, message: '파일명이 너무 깁니다.' };
  }

  return { success: true };
}

/**
 * 일반 파일 검증 (백엔드와 동일한 로직)
 * 내부 사용 전용
 */
function validateFile(file) {
  if (!file) {
    return { success: false, message: '파일이 제공되지 않았습니다.' };
  }

  // 파일명 검증
  if (!file.name || file.name.trim() === '') {
    return { success: false, message: '파일명이 올바르지 않습니다.' };
  }

  // 파일명 길이 검증 (UTF-8 바이트 기준 255바이트)
  const filenameBytes = new TextEncoder().encode(file.name).length;
  if (filenameBytes > 255) {
    return { success: false, message: '파일명이 너무 깁니다.' };
  }

  // MIME 타입 검증
  const allowedMimeTypes = [
    ...FILE_LIMITS.file.image.allowedTypes,
    ...FILE_LIMITS.file.pdf.allowedTypes
  ];
  
  if (!file.type || !allowedMimeTypes.includes(file.type)) {
    return { success: false, message: '지원하지 않는 파일 형식입니다.' };
  }

  // 확장자-MIME 일치 검증
  const extension = getFileExtension(file.name).toLowerCase();
  let fileConfig = null;
  
  if (FILE_LIMITS.file.image.allowedTypes.includes(file.type)) {
    fileConfig = FILE_LIMITS.file.image;
  } else if (FILE_LIMITS.file.pdf.allowedTypes.includes(file.type)) {
    fileConfig = FILE_LIMITS.file.pdf;
  }

  if (!fileConfig) {
    return { success: false, message: '지원하지 않는 파일 형식입니다.' };
  }

  if (!fileConfig.allowedExtensions.includes(extension)) {
    const fileType = file.type.startsWith('image/') ? '이미지' : 'PDF 문서';
    return { success: false, message: `${fileType} 확장자가 올바르지 않습니다.` };
  }

  // 타입별 크기 제한 검증
  if (file.size > fileConfig.maxSize) {
    const fileType = file.type.startsWith('image/') ? '이미지' : 'PDF 문서';
    const limitMB = Math.floor(fileConfig.maxSize / 1024 / 1024);
    return { success: false, message: `${fileType} 파일은 ${limitMB}MB를 초과할 수 없습니다.` };
  }

  return { success: true };
}

/**
 * S3에 파일 직접 업로드
 * @param {File} file - 업로드할 파일
 * @param {string} type - 'files' 또는 'profiles'
 * @param {Function} onProgress - 진행률 콜백 함수 (0-100)
 * @param {boolean} skipValidation - 검증 건너뛰기 (기본값: false)
 * @returns {Promise<{success: boolean, s3Key?: string, url?: string, error?: string}>}
 */
export async function uploadToS3(file, type = 'files', onProgress = null, skipValidation = false) {
  try {
    // 파일 검증 (skipValidation이 false인 경우)
    if (!skipValidation) {
      const validation = type === 'profiles' 
        ? validateProfileImage(file)
        : validateFile(file);
      
      if (!validation.success) {
        return {
          success: false,
          error: validation.message || '파일 검증에 실패했습니다.'
        };
      }
    }

    // 파일명 생성
    const safeFileName = generateSafeFileName(file.name);
    const uploadPath = S3_CONFIG.uploadPaths[type] || S3_CONFIG.uploadPaths.files;
    const s3Key = `${uploadPath}/${safeFileName}`;
    const uploadUrl = `${S3_CONFIG.baseUrl}/${s3Key}`;

    // XMLHttpRequest를 사용하여 업로드 진행률 추적
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();

      // 진행률 추적
      if (onProgress) {
        xhr.upload.addEventListener('progress', (e) => {
          if (e.lengthComputable) {
            const percentCompleted = Math.round((e.loaded * 100) / e.total);
            onProgress(percentCompleted);
          }
        });
      }

      // 완료 처리
      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          const publicUrl = `${S3_CONFIG.cloudFrontUrl}/${s3Key}`;
          resolve({
            success: true,
            s3Key,
            url: publicUrl,
            filename: safeFileName,
            originalFilename: normalizeOriginalFilename(file.name)
          });
        } else {
          reject(new Error(`S3 업로드 실패: HTTP ${xhr.status}`));
        }
      });

      // 에러 처리
      xhr.addEventListener('error', () => {
        reject(new Error('S3 업로드 중 네트워크 오류가 발생했습니다.'));
      });

      xhr.addEventListener('abort', () => {
        reject(new Error('S3 업로드가 취소되었습니다.'));
      });

      // PUT 요청으로 파일 업로드
      xhr.open('PUT', uploadUrl);
      xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
      
      // 버킷 정책에서 이미 공개 쓰기를 허용하므로 ACL 헤더 불필요
      // xhr.setRequestHeader('x-amz-acl', 'public-read');
      
      xhr.send(file);
    });
  } catch (error) {
    return {
      success: false,
      error: error.message || 'S3 업로드 중 오류가 발생했습니다.'
    };
  }
}

