package com.ktb.chatapp.service;

import com.ktb.chatapp.model.File;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadResult uploadFile(MultipartFile file, String uploaderId);

    String storeFile(MultipartFile file, String subDirectory);

    Resource loadFileAsResource(String fileName, String requesterId);

    boolean deleteFile(String fileId, String requesterId);

    void deleteStoredFile(String storedPath);

    /**
     * 파일 메타데이터 저장 (S3 업로드 완료 후)
     */
    File saveFileMetadata(String filename, String originalFilename, String contentType, long fileSize, String s3Key, String uploaderId);

    /**
     * 프론트엔드에서 직접 S3에 업로드한 파일의 메타데이터 저장
     */
    FileUploadResult saveFileMetadataFromS3(String filename, String originalFilename, String contentType, long fileSize, String s3Key, String uploaderId);

    /**
     * 파일의 CloudFront 공개 URL 반환
     */
    String getFilePublicUrl(String filename);
}
