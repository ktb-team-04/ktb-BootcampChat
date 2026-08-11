package com.ktb.chatapp.service.message;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BufferedMessageWriteService {

    private static final String BUFFERED_MODE = "buffered";

    private final MessageRepository messageRepository;
    private final String writeMode;
    private final int batchSize;
    private final BlockingQueue<Message> queue;

    public BufferedMessageWriteService(
            MessageRepository messageRepository,
            @Value("${app.message.write.mode:buffered}") String writeMode,
            @Value("${app.message.buffer.batch-size:100}") int batchSize,
            @Value("${app.message.buffer.max-size:5000}") int maxSize) {
        this.messageRepository = messageRepository;
        this.writeMode = writeMode;
        this.batchSize = Math.max(1, batchSize);
        this.queue = new ArrayBlockingQueue<>(Math.max(1, maxSize));
    }

    public Message write(Message message, boolean bufferable) {
        if (!bufferable || !BUFFERED_MODE.equalsIgnoreCase(writeMode)) {
            return messageRepository.save(message);
        }

        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        if (!queue.offer(message)) {
            log.warn("메시지 버퍼가 가득 차 메시지를 드롭합니다: messageId={}, roomId={}",
                    message.getId(), message.getRoomId());
        }

        return message;
    }

    @Scheduled(fixedDelayString = "${app.message.buffer.flush-interval-ms:100}")
    public void flush() {
        if (!BUFFERED_MODE.equalsIgnoreCase(writeMode)) {
            return;
        }

        List<Message> batch = drainBatch();
        if (batch.isEmpty()) {
            return;
        }

        try {
            messageRepository.saveAll(batch);
            log.debug("메시지 버퍼 batch 저장 완료: count={}", batch.size());
        } catch (Exception e) {
            log.error("메시지 버퍼 batch 저장 실패. 메시지는 재시도하지 않고 드롭됩니다: count={}", batch.size(), e);
        }
    }

    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    private List<Message> drainBatch() {
        List<Message> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }
}
