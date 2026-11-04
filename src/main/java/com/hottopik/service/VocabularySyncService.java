package com.hottopik.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dịch vụ đồng bộ dữ liệu thời gian thực (Real-time synchronization) cho Từ vựng.
 * Quản lý các kết nối Server-Sent Events (SSE) để truyền phát thông báo thay đổi tức thì
 * từ Content Manager sang phía Học viên (Learner/Student) và các tab quản trị khác mà không cần F5.
 */
@Service
public class VocabularySyncService {

    private static final Logger log = LoggerFactory.getLogger(VocabularySyncService.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 phút timeout cho kết nối SSE

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Gửi sự kiện kết nối ban đầu
        try {
            Map<String, Object> welcome = new HashMap<>();
            welcome.put("status", "CONNECTED");
            welcome.put("timestamp", System.currentTimeMillis());
            welcome.put("activeClients", emitters.size());

            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(welcome));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Phát thông báo thay đổi đến tất cả người dùng (Content Manager & Students)
     *
     * @param action Tên hành động: VOCAB_SAVED, VOCAB_TOGGLED, TOPIC_CHANGED, BULK_CREATED, LEVEL_TOGGLED
     * @param payload Dữ liệu chi tiết đính kèm (id từ vựng, cấp độ, chủ đề, ...)
     */
    public void broadcast(String action, Map<String, Object> payload) {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>(payload != null ? payload : Map.of());
        data.put("action", action);
        data.put("timestamp", System.currentTimeMillis());

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("vocab_sync")
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Đã gỡ bỏ {} kết nối SSE đã đóng.", deadEmitters.size());
        }
    }

    public int getActiveClientCount() {
        return emitters.size();
    }
}
