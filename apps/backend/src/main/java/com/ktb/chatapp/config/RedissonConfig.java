package com.ktb.chatapp.config;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides a RedissonClient that can be reused by the Socket.IO store factory.
 */
@Configuration
@ConditionalOnProperty(name = "socketio.store", havingValue = "redis", matchIfMissing = true)
public class RedissonConfig {

    private final RedisProperties redisProperties;
    @Value("${spring.data.redis.ssl:false}")
    private boolean sslEnabled;

    public RedissonConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient redissonClient() {
        Config config = new Config();
        if (isSentinelMode()) {
            buildSentinelConfig(config);
        } else if (isClusterMode()) {
            buildClusterConfig(config);
        } else {
            buildSingleServerConfig(config);
        }
        return Redisson.create(config);
    }

    private void buildSingleServerConfig(Config config) {
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(resolveSingleServerAddress())
                .setDatabase(redisProperties.getDatabase());
        applyCommonServerSettings(serverConfig);
    }

    private void buildSentinelConfig(Config config) {
        SentinelServersConfig sentinelConfig = config.useSentinelServers()
                .setMasterName(redisProperties.getSentinel().getMaster())
                .addSentinelAddress(formatNodes(redisProperties.getSentinel().getNodes()))
                .setDatabase(redisProperties.getDatabase());
        applyCommonServerSettings(sentinelConfig);
    }

    private void buildClusterConfig(Config config) {
        ClusterServersConfig clusterConfig = config.useClusterServers()
                .addNodeAddress(formatNodes(redisProperties.getCluster().getNodes()));
        applyCommonServerSettings(clusterConfig);
    }

    private void applyCommonServerSettings(SingleServerConfig serverConfig) {
        serverConfig.setTimeout(timeoutInMillis());
        serverConfig.setClientName(redisProperties.getClientName());
        if (redisProperties.getUsername() != null) {
            serverConfig.setUsername(redisProperties.getUsername());
        }
        if (hasPassword()) {
            serverConfig.setPassword(redisProperties.getPassword());
        }
    }

    private void applyCommonServerSettings(SentinelServersConfig serverConfig) {
        serverConfig.setTimeout(timeoutInMillis());
        serverConfig.setClientName(redisProperties.getClientName());
        if (redisProperties.getUsername() != null) {
            serverConfig.setUsername(redisProperties.getUsername());
        }
        if (hasPassword()) {
            serverConfig.setPassword(redisProperties.getPassword());
        }
    }

    private String resolveSingleServerAddress() {
        if (redisProperties.getUrl() != null && !redisProperties.getUrl().isBlank()) {
            return redisProperties.getUrl();
        }
        String host = redisProperties.getHost();
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        int port = redisProperties.getPort();
        if (port <= 0) {
            port = 6379;
        }
        return withProtocol(host + ":" + port);
    }

    private void applyCommonServerSettings(ClusterServersConfig serverConfig) {
        serverConfig.setTimeout(timeoutInMillis());
        serverConfig.setClientName(redisProperties.getClientName());
        if (redisProperties.getUsername() != null) {
            serverConfig.setUsername(redisProperties.getUsername());
        }
        if (hasPassword()) {
            serverConfig.setPassword(redisProperties.getPassword());
        }
    }

    private String[] formatNodes(List<String> nodes) {
        return nodes.stream()
                .map(this::withProtocol)
                .collect(Collectors.toList())
                .toArray(new String[0]);
    }

    private String withProtocol(String address) {
        return (sslEnabled ? "rediss://" : "redis://") + address;
    }

    private boolean isSentinelMode() {
        return redisProperties.getSentinel() != null
                && redisProperties.getSentinel().getNodes() != null
                && !redisProperties.getSentinel().getNodes().isEmpty();
    }

    private boolean isClusterMode() {
        return redisProperties.getCluster() != null
                && redisProperties.getCluster().getNodes() != null
                && !redisProperties.getCluster().getNodes().isEmpty();
    }

    private boolean hasPassword() {
        return redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank();
    }

    private int timeoutInMillis() {
        Duration timeout = redisProperties.getTimeout();
        return timeout != null ? (int) timeout.toMillis() : 10000;
    }
}
