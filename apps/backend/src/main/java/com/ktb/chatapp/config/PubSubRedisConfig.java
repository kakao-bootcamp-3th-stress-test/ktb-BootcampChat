package com.ktb.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pub/Sub 전용 Redis 인스턴스를 위한 Redisson 클라이언트 설정.
 *
 * app.pubsub-redis.enabled=true 이고 PUBSUB_REDIS_HOST/PORT 가 설정되어 있으면
 * 메인 Redis와는 별도로 pub/sub 용 Redis 인스턴스를 사용한다.
 * 그렇지 않으면 기본 Redis 설정을 그대로 재사용한다.
 */
@Configuration
@EnableConfigurationProperties(PubSubRedisConfig.PubSubRedisProperties.class)
public class PubSubRedisConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "app.pubsub-redis.enabled", havingValue = "true")
    public RedissonClient pubSubRedissonClient(PubSubRedisProperties properties) {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(buildAddress(properties))
                .setDatabase(properties.getDatabase());

        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            serverConfig.setPassword(properties.getPassword());
        }
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            serverConfig.setUsername(properties.getUsername());
        }

        serverConfig.setTimeout(properties.getTimeout());
        serverConfig.setClientName(properties.getClientName());

        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnProperty(name = "app.pubsub-redis.enabled", havingValue = "true")
    public ObjectMapper pubSubObjectMapper(ObjectMapper objectMapper) {
        // 메인 ObjectMapper 설정을 그대로 재사용 (커스텀 모듈 포함)
        return objectMapper;
    }

    private String buildAddress(PubSubRedisProperties properties) {
        String protocol = properties.isSsl() ? "rediss://" : "redis://";
        return protocol + properties.getHost() + ":" + properties.getPort();
    }

    @Getter
    @Setter
    @ToString
    @ConfigurationProperties(prefix = "app.pubsub-redis")
    public static class PubSubRedisProperties {
        /**
         * Pub/Sub Redis 호스트 (기본값: main Redis host 로부터 application.properties 에서 상속).
         */
        private String host = "localhost";

        /**
         * Pub/Sub Redis 포트 (기본값: main Redis port 로부터 application.properties 에서 상속).
         */
        private int port = 6379;

        /**
         * TLS(rediss) 사용 여부.
         */
        private boolean ssl = false;

        /**
         * 선택적 인증 정보.
         */
        private String username;
        private String password;

        /**
         * 논리 DB 인덱스 (단일 인스턴스 모드에서만 사용).
         */
        private int database = 0;

        /**
         * 클라이언트 이름 (모니터링/디버깅용).
         */
        private String clientName = "ktb-chat-backend-pubsub";

        /**
         * 타임아웃(ms).
         */
        private int timeout = 5000;
    }
}

