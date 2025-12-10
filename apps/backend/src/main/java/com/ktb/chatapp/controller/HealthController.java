package com.ktb.chatapp.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        // HashMap 생성 오버헤드 제거, 직접 JSON 문자열 반환
        // 최소한의 메모리 할당과 직렬화 오버헤드
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        
        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body("{\"status\":\"ok\"}");
    }
}
