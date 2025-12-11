package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;

/**
 * 메시지 브로드캐스트 전에 큐잉을 담당하는 인터페이스.
 */
public interface MessageDispatchQueue {

    void enqueue(MessageResponse response);
}
