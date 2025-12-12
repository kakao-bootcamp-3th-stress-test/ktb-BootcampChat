package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;

/**
 * 채팅 메시지를 소켓으로 브로드캐스트하는 전략.
 */
public interface MessageBroadcastStrategy {

    void broadcast(MessageResponse response);
}
