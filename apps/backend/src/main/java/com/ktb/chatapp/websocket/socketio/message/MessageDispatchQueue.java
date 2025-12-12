package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;

/**
 * 메시지 디스패치 큐 인터페이스
 */
public interface MessageDispatchQueue {
    /**
     * 메시지를 큐에 넣는다.
     * 
     * @param response 처리할 메시지
     */
    void enqueue(MessageResponse response);
}
