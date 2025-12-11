package com.ktb.chatapp.dto.rooms;

import java.time.LocalDateTime;

/**
 * 최소 필드와 참가자 수만 포함한 방 요약 정보.
 */
public record RoomSummary(
        String id,
        String name,
        String creator,
        boolean hasPassword,
        LocalDateTime createdAt,
        long participantsCount) {
}
