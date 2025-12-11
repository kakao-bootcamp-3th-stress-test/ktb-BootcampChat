package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 큐를 사용하지 않고 즉시 전송하는 전략.
 */
@Component
@ConditionalOnProperty(name = "chat.message.queue.mode", havingValue = "immediate")
@RequiredArgsConstructor
public class ImmediateMessageDispatchQueue implements MessageDispatchQueue {

    private final MessageDeliveryService messageDeliveryService;

    @Override
    public void enqueue(MessageResponse response) {
        messageDeliveryService.deliver(response);
    }
}
