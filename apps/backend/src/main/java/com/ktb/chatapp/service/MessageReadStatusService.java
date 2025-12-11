package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * 메시지 읽음 상태 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadStatusService {

    private final MongoTemplate mongoTemplate;

    /**
     * 메시지 읽음 상태 업데이트
     *
     * @param messageIds 읽음 상태를 업데이트할 메시지 리스트
     * @param userId 읽은 사용자 ID
     */
    public void updateReadStatus(List<String> messageIds, String userId) {
        if (messageIds.isEmpty()) {
            return;
        }
        
        try {
            Message.MessageReader readerInfo = Message.MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .build();

            BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Message.class);
            for (String messageId : messageIds) {
                Query query = new Query(Criteria.where("_id").is(messageId)
                        .and("readers.userId").ne(userId));
                Update update = new Update().addToSet("readers", readerInfo);
                ops.updateOne(query, update);
            }

            var result = ops.execute();
            log.debug("Read status bulk update - matched: {}, modified: {}, user: {}",
                    result.getMatchedCount(), result.getModifiedCount(), userId);

        } catch (Exception e) {
            log.error("Read status update error for user {}", userId, e);
        }
    }
}
