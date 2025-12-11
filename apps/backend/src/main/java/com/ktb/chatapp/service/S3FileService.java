package com.ktb.chatapp.service;

import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.util.FileUtil;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3-backed FileService implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.file", name = "storage", havingValue = "s3")
public class S3FileService implements FileService {

    private final S3Client s3Client;
    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;

    @Value("${app.file.s3.bucket}")
    private String bucketName;

    @Value("${app.file.public-base-url:}")
    private String publicBaseUrl;

    @PostConstruct
    public void validateConfiguration() {
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalStateException("S3 bucket name must be configured. Set FILE_S3_BUCKET environment variable.");
        }
        log.info("S3FileService initialized with bucket: {}", bucketName);
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String uploaderId) {
        try {
            FileUtil.validateFile(file);
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "file";
            }
            originalFilename = StringUtils.cleanPath(originalFilename);
            String safeFileName = FileUtil.generateSafeFileName(originalFilename);
            String key = "uploads/" + safeFileName;

            uploadToS3(file, key, false);

            File savedFile = fileRepository.save(File.builder()
                    .filename(safeFileName)
                    .originalname(FileUtil.normalizeOriginalFilename(originalFilename))
                    .mimetype(file.getContentType())
                    .size(file.getSize())
                    .path(key)
                    .user(uploaderId)
                    .uploadDate(LocalDateTime.now())
                    .build());

            return FileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .build();

        } catch (Exception e) {
            log.error("S3 file upload failed", e);
            throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        try {
            FileUtil.validateFile(file);
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "file";
            }
            originalFilename = StringUtils.cleanPath(originalFilename);
            String safeFileName = FileUtil.generateSafeFileName(originalFilename);
            String key = (subDirectory != null && !subDirectory.isBlank())
                    ? subDirectory + "/" + safeFileName
                    : safeFileName;

            log.info("Storing file to S3 - Key: {}, Size: {}, ContentType: {}", key, file.getSize(), file.getContentType());
            uploadToS3(file, key, true);

            String publicUrl = buildPublicUrl(key);
            log.info("File stored successfully - Key: {}, URL: {}", key, publicUrl);
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to store file - SubDirectory: {}, Error: {}", subDirectory, e.getMessage(), e);
            throw new RuntimeException("프로필 이미지를 저장할 수 없습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error storing file - SubDirectory: {}", subDirectory, e);
            throw new RuntimeException("파일 저장 중 예기치 않은 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public Resource loadFileAsResource(String fileName, String requesterId) {
        try {
            File fileEntity = fileRepository.findByFilename(fileName)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + fileName));

            Message message = messageRepository.findByFileId(fileEntity.getId())
                    .orElseThrow(() -> new RuntimeException("파일과 연결된 메시지를 찾을 수 없습니다"));

            Room room = roomRepository.findById(message.getRoomId())
                    .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다"));

            if (!room.getParticipantIds().contains(requesterId)) {
                throw new RuntimeException("파일에 접근할 권한이 없습니다");
            }

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileEntity.getPath())
                    .build());

            return new InputStreamResource(s3Object) {
                @Override
                public long contentLength() throws IOException {
                    return s3Object.response().contentLength();
                }

                @Override
                public String getFilename() {
                    return fileEntity.getOriginalname();
                }
            };

        } catch (Exception e) {
            log.error("S3 load file error: {}", fileName, e);
            throw new RuntimeException("파일을 불러올 수 없습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
            }

            deleteObject(fileEntity.getPath());
            fileRepository.delete(fileEntity);
            log.info("S3 file deleted: {}", fileEntity.getPath());
            return true;
        } catch (Exception e) {
            log.error("S3 delete file error: {}", fileId, e);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public void deleteStoredFile(String storedPath) {
        String key = extractKeyFromUrl(storedPath);
        if (!StringUtils.hasText(key)) {
            return;
        }
        deleteObject(key);
    }

    @Override
    public File saveFileMetadata(String filename, String originalFilename, String contentType, long fileSize, String s3Key, String uploaderId) {
        File savedFile = fileRepository.save(File.builder()
                .filename(filename)
                .originalname(FileUtil.normalizeOriginalFilename(originalFilename))
                .mimetype(contentType)
                .size(fileSize)
                .path(s3Key)
                .user(uploaderId)
                .uploadDate(LocalDateTime.now())
                .build());
        log.info("File metadata saved - ID: {}, S3Key: {}", savedFile.getId(), s3Key);
        return savedFile;
    }

    @Override
    public FileUploadResult saveFileMetadataFromS3(String filename, String originalFilename, String contentType, long fileSize, String s3Key, String uploaderId) {
        try {
            // 파일명이 없으면 S3 키에서 추출
            if (filename == null || filename.isEmpty()) {
                filename = extractFilenameFromS3Key(s3Key);
            }
            
            // 원본 파일명이 없으면 파일명 사용
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = filename;
            }

            File savedFile = saveFileMetadata(filename, originalFilename, contentType, fileSize, s3Key, uploaderId);

            return FileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .build();
        } catch (Exception e) {
            log.error("S3 file metadata save failed - s3Key: {}, Error: {}", s3Key, e.getMessage(), e);
            throw new RuntimeException("파일 메타데이터 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * S3 키에서 파일명 추출
     */
    private String extractFilenameFromS3Key(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return "file";
        }
        int lastSlash = s3Key.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < s3Key.length() - 1 
            ? s3Key.substring(lastSlash + 1) 
            : s3Key;
    }

    @Override
    public String getFilePublicUrl(String filename) {
        File fileEntity = fileRepository.findByFilename(filename)
                .orElse(null);
        if (fileEntity == null) {
            return null;
        }
        return buildPublicUrl(fileEntity.getPath());
    }

    private void uploadToS3(MultipartFile file, String key, boolean publicRead) throws IOException {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType());
            
            // OAC 사용 시 ACL은 무시되므로 제거 (에러 방지)
            // 버킷 정책과 OAC로 접근 제어
            
            PutObjectRequest request = requestBuilder.build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded object to S3: {}/{}", bucketName, key);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("S3 upload failed - Bucket: {}, Key: {}, Error: {}, Status: {}", 
                    bucketName, key, e.getMessage(), e.statusCode(), e);
            throw new IOException("S3 업로드 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("S3 upload error - Bucket: {}, Key: {}", bucketName, key, e);
            throw new IOException("파일 업로드 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private void deleteObject(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        log.info("Deleted object from S3: {}/{}", bucketName, key);
    }

    private String buildPublicUrl(String key) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            log.warn("publicBaseUrl이 설정되지 않았습니다. Key만 반환: {}", key);
            return key;
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String url = base + "/" + key;
        log.debug("Public URL 생성 - Key: {}, URL: {}", key, url);
        return url;
    }

    private String extractKeyFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        if (StringUtils.hasText(publicBaseUrl) && url.startsWith(publicBaseUrl)) {
            String key = url.substring(publicBaseUrl.length());
            return key.startsWith("/") ? key.substring(1) : key;
        }
        if (url.startsWith("http")) {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                return path.startsWith("/") ? path.substring(1) : path;
            } catch (Exception e) {
                log.warn("Failed to parse S3 URL: {}", url);
                return null;
            }
        }
        return url;
    }
}
