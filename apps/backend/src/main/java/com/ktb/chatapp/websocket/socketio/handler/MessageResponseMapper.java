package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.repository.FileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 메시지를 응답 DTO로 변환하는 매퍼
 * 파일 정보, 사용자 정보 등을 포함한 MessageResponse 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageResponseMapper {

    private final FileRepository fileRepository;

    /**
     * Message 엔티티를 MessageResponse DTO로 변환
     *
     * @param message 변환할 메시지 엔티티
     * @return MessageResponse DTO
     */
    public MessageResponse mapToMessageResponse(Message message) {
        return mapToMessageResponse(message, null);
    }

    public MessageResponse mapToMessageResponse(Message message, Map<String, File> preloadedFiles) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.toTimestampMillis())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .reactions(message.getReactions() != null ?
                        message.getReactions() : new HashMap<>())
                .readers(message.getReaders() != null ?
                        message.getReaders() : new ArrayList<>());

        attachFileMetadata(message, builder, preloadedFiles);

        // 메타데이터 설정
        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }

    private void attachFileMetadata(Message message,
                                    MessageResponse.MessageResponseBuilder builder,
                                    Map<String, File> preloadedFiles) {
        String fileId = message.getFileId();
        if (fileId == null) {
            return;
        }

        File file = null;
        if (preloadedFiles != null) {
            file = preloadedFiles.get(fileId);
        }
        if (file == null) {
            file = fileRepository.findById(fileId).orElse(null);
        }
        if (file == null) {
            return;
        }

        builder.file(mapToFileResponse(file));
    }

    private FileResponse mapToFileResponse(File file) {
        return FileResponse.builder()
                .id(file.getId())
                .filename(file.getFilename())
                .originalname(file.getOriginalname())
                .mimetype(file.getMimetype())
                .size(file.getSize())
                .build();
    }
}
