package com.ktb.chatapp.websocket.socketio;

import com.ktb.chatapp.dto.message.MessageResponse;

/**
 * 채팅 메시지 브로드캐스트 전략 인터페이스.
 *
 * 구현체에 따라 직접 Socket.IO 로 보내거나,
 * Redis Pub/Sub 을 통해 다른 인스턴스들과 공유할 수 있다.
 */
public interface ChatMessagePublisher {

    /**
     * 새로 처리된 채팅 메시지를 브로드캐스트한다.
     *
     * @param messageResponse 전송할 메시지 응답 DTO
     */
    void publish(MessageResponse messageResponse);
}

