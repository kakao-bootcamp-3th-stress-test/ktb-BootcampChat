package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.websocket.socketio.ChatMessagePublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 채팅 메시지 처리를 위한 비동기 큐.
 * 
 * 메시지를 큐에 넣고(enqueue), 별도 워커 스레드에서 순차적으로 처리한다.
 * - FIFO 순서 보장
 * - 배치 처리 가능
 * - 백프레셔 지원 (큐 크기 제한)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatchQueue {

    @Value("${app.message-queue.capacity:10000}")
    private int queueCapacity;

    @Value("${app.message-queue.worker-threads:4}")
    private int workerThreads;

    @Value("${app.message-queue.batch-size:1}")
    private int batchSize;

    private final ChatMessagePublisher chatMessagePublisher;
    
    private BlockingQueue<MessageResponse> messageQueue;
    private ThreadPoolExecutor workerExecutor;
    private volatile boolean running = true;
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        // 큐 생성
        messageQueue = new LinkedBlockingQueue<>(queueCapacity);
        
        // 워커 스레드 풀 생성
        workerExecutor = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "message-dispatch-worker");
                    t.setDaemon(true);
                    return t;
                }
        );

        // 워커 스레드 시작
        for (int i = 0; i < workerThreads; i++) {
            final int workerId = i;
            workerExecutor.submit(() -> processQueue(workerId));
        }

        log.info("MessageDispatchQueue initialized - capacity: {}, workers: {}, batch-size: {}",
                queueCapacity, workerThreads, batchSize);
    }

    /**
     * 메시지를 큐에 넣는다. 즉시 반환된다.
     * 
     * @param messageResponse 처리할 메시지
     * @return 큐에 성공적으로 추가되었으면 true, 큐가 가득 차서 실패하면 false
     */
    public boolean enqueue(MessageResponse messageResponse) {
        if (messageResponse == null || messageResponse.getRoomId() == null) {
            log.warn("Skipping enqueue of null/invalid messageResponse: {}", messageResponse);
            return false;
        }

        boolean offered = messageQueue.offer(messageResponse);
        if (!offered) {
            log.warn("Message queue is full, dropping message. roomId: {}, queueSize: {}",
                    messageResponse.getRoomId(), messageQueue.size());
            failedCount.incrementAndGet();
        }
        return offered;
    }

    /**
     * 워커 스레드에서 큐를 처리하는 메서드.
     */
    private void processQueue(int workerId) {
        log.info("Message dispatch worker {} started", workerId);
        while (running || !messageQueue.isEmpty()) {
            try {
                if (batchSize > 1) {
                    processBatch();
                } else {
                    processSingle();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Message dispatch worker {} interrupted", workerId, e);
                break;
            } catch (Exception e) {
                log.error("Error in message dispatch worker {}", workerId, e);
                failedCount.incrementAndGet();
            }
        }
        log.info("Message dispatch worker {} stopped", workerId);
    }

    /**
     * 단일 메시지 처리.
     */
    private void processSingle() throws InterruptedException {
        MessageResponse message = messageQueue.poll(1, TimeUnit.SECONDS);
        if (message != null) {
            processMessage(message);
        }
    }

    /**
     * 배치 처리 (여러 메시지를 모아서 처리).
     */
    private void processBatch() throws InterruptedException {
        List<MessageResponse> batch = new ArrayList<>(batchSize);
        
        // 첫 메시지는 blocking으로 대기
        MessageResponse first = messageQueue.take();
        batch.add(first);
        
        // 나머지는 non-blocking으로 수집
        messageQueue.drainTo(batch, batchSize - 1);
        
        // 배치 처리
        for (MessageResponse message : batch) {
            processMessage(message);
        }
    }

    /**
     * 실제 메시지 처리.
     */
    private void processMessage(MessageResponse messageResponse) {
        try {
            chatMessagePublisher.publish(messageResponse);
            processedCount.incrementAndGet();
            log.debug("Message processed from queue. roomId: {}, queueSize: {}",
                    messageResponse.getRoomId(), messageQueue.size());
        } catch (Exception e) {
            log.error("Failed to process message from queue. roomId: {}",
                    messageResponse.getRoomId(), e);
            failedCount.incrementAndGet();
        }
    }

    /**
     * 큐 크기 반환.
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * 처리된 메시지 수 반환.
     */
    public long getProcessedCount() {
        return processedCount.get();
    }

    /**
     * 실패한 메시지 수 반환.
     */
    public long getFailedCount() {
        return failedCount.get();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("MessageDispatchQueue shutdown completed. Processed: {}, Failed: {}",
                processedCount.get(), failedCount.get());
    }
}
