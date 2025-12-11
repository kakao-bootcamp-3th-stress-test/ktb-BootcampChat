package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.FileService;
import com.ktb.chatapp.service.FileUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일 (Files)", description = "파일 업로드 및 다운로드 API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    /**
     * 파일 업로드
     */
    @Operation(summary = "파일 업로드", description = "파일을 업로드합니다. 최대 50MB까지 가능합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 업로드 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 파일",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "413", description = "파일 크기 초과",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "업로드할 파일 (백엔드 업로드 시 필요)") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "S3 키 (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "s3Key", required = false) String s3Key,
            @Parameter(description = "S3 URL (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "s3Url", required = false) String s3Url,
            @Parameter(description = "파일명 (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "filename", required = false) String filename,
            @Parameter(description = "원본 파일명 (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "originalFilename", required = false) String originalFilename,
            @Parameter(description = "컨텐츠 타입 (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "contentType", required = false) String contentType,
            @Parameter(description = "파일 크기 (프론트엔드에서 직접 업로드한 경우)") @RequestParam(value = "fileSize", required = false) Long fileSize,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            FileUploadResult result;
            
            // 프론트엔드에서 이미 S3에 업로드한 경우 메타데이터만 저장 (파일 없이)
            if (s3Key != null && !s3Key.isEmpty() && s3Url != null && !s3Url.isEmpty()) {
                log.info("프론트엔드에서 직접 업로드된 파일 메타데이터 저장 - s3Key: {}, filename: {}", s3Key, filename);
                // 프론트엔드에서 이미 검증 완료했으므로 파일 없이 메타데이터만 저장
                result = fileService.saveFileMetadataFromS3(
                    filename != null ? filename : extractFilenameFromS3Key(s3Key),
                    originalFilename != null ? originalFilename : filename,
                    contentType,
                    fileSize != null ? fileSize : 0L,
                    s3Key,
                    user.getId()
                );
            } else {
                // 기존 로직: 백엔드에서 파일 업로드 (하위 호환성)
                if (file == null || file.isEmpty()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "파일이 제공되지 않았습니다.");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
                log.info("백엔드에서 파일 업로드 처리");
                result = fileService.uploadFile(file, user.getId());
            }

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일 업로드 성공");
                
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("_id", result.getFile().getId());
                fileData.put("filename", result.getFile().getFilename());
                fileData.put("originalname", result.getFile().getOriginalname());
                fileData.put("mimetype", result.getFile().getMimetype());
                fileData.put("size", result.getFile().getSize());
                fileData.put("uploadDate", result.getFile().getUploadDate());
                
                response.put("file", fileData);

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 업로드에 실패했습니다.");
                return ResponseEntity.status(500).body(errorResponse);
            }

        } catch (Exception e) {
            log.error("파일 업로드 중 에러 발생", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 업로드 중 오류가 발생했습니다.");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
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

    /**
     * 보안이 강화된 파일 다운로드
     */
    @Operation(summary = "파일 다운로드", description = "업로드된 파일을 다운로드합니다. 본인이 업로드한 파일만 다운로드 가능합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 다운로드 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "403", description = "권한 없음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "다운로드할 파일명") @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            Resource resource = fileService.loadFileAsResource(filename, user.getId());

            File fileEntity = fileRepository.findByFilename(filename)
                    .orElse(null);

            String originalFilename = fileEntity != null ? fileEntity.getOriginalname() : filename;
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String contentDisposition = String.format(
                    "attachment; filename*=UTF-8''%s",
                    encodedFilename
            );

            long contentLength = fileEntity != null ? fileEntity.getSize() : resource.contentLength();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileEntity.getMimetype()))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                    .body(resource);

        } catch (Exception e) {
            log.error("파일 다운로드 중 에러 발생: {}", filename, e);
            return handleFileError(e);
        }
    }

    private ResponseEntity<?> handleFileError(Exception e) {
        String errorMessage = e.getMessage();
        int statusCode = 500;
        String responseMessage = "파일 처리 중 오류가 발생했습니다.";

        if (errorMessage != null) {
            if (errorMessage.contains("잘못된 파일명") || errorMessage.contains("Invalid filename")) {
                statusCode = 400;
                responseMessage = "잘못된 파일명입니다.";
            } else if (errorMessage.contains("인증") || errorMessage.contains("Authentication")) {
                statusCode = 401;
                responseMessage = "인증이 필요합니다.";
            } else if (errorMessage.contains("잘못된 파일 경로") || errorMessage.contains("Invalid file path")) {
                statusCode = 400;
                responseMessage = "잘못된 파일 경로입니다.";
            } else if (errorMessage.contains("찾을 수 없습니다") || errorMessage.contains("not found")) {
                statusCode = 404;
                responseMessage = "파일을 찾을 수 없습니다.";
            } else if (errorMessage.contains("메시지를 찾을 수 없습니다")) {
                statusCode = 404;
                responseMessage = "파일 메시지를 찾을 수 없습니다.";
            } else if (errorMessage.contains("권한") || errorMessage.contains("Unauthorized")) {
                statusCode = 403;
                responseMessage = "파일에 접근할 권한이 없습니다.";
            }
        }

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", responseMessage);

        return ResponseEntity.status(statusCode).body(errorResponse);
    }

    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<?> viewFile(
            @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            if (principal == null) {
                log.warn("파일 미리보기 인증 실패: {} (Principal이 null)", filename);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "인증이 필요합니다.");
                return ResponseEntity.status(401).body(errorResponse);
            }

            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            File fileEntity = fileRepository.findByFilename(filename)
                    .orElseThrow(() -> {
                        log.warn("파일 엔티티를 찾을 수 없음: {} (사용자: {})", filename, user.getId());
                        return new RuntimeException("파일을 찾을 수 없습니다.");
                    });

            if (!fileEntity.isPreviewable()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "미리보기를 지원하지 않는 파일 형식입니다.");
                return ResponseEntity.status(415).body(errorResponse);
            }

            Resource resource = fileService.loadFileAsResource(filename, user.getId());

            String originalFilename = fileEntity.getOriginalname();
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String contentDisposition = String.format(
                    "inline; filename=\"%s\"; filename*=UTF-8''%s",
                    originalFilename,
                    encodedFilename
            );

            long contentLength = fileEntity.getSize();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileEntity.getMimetype()))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (Exception e) {
            log.error("파일 미리보기 중 에러 발생: {} - {}", filename, e.getMessage(), e);
            return handleFileError(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            boolean deleted = fileService.deleteFile(id, user.getId());

            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일이 삭제되었습니다.");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 삭제에 실패했습니다.");
                return ResponseEntity.status(400).body(errorResponse);
            }

        } catch (RuntimeException e) {
            log.error("파일 삭제 중 에러 발생: {}", id, e);
            String errorMessage = e.getMessage();
            
            if (errorMessage != null && errorMessage.contains("찾을 수 없습니다")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 찾을 수 없습니다.");
                return ResponseEntity.status(404).body(errorResponse);
            } else if (errorMessage != null && errorMessage.contains("권한")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 삭제할 권한이 없습니다.");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 삭제 중 오류가 발생했습니다.");
            errorResponse.put("error", errorMessage);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
