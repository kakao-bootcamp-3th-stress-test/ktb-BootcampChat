package com.ktb.chatapp.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        // 부하테스트 최적화: 최소한의 응답만 반환
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        
        // 캐시 헤더로 불필요한 재요청 방지
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(body);
    }
}
