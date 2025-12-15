package com.ktb.chatapp.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${app.mongo.pool.max-size:200}")
    private int maxSize;

    @Value("${app.mongo.pool.min-size:20}")
    private int minSize;

    @Value("${app.mongo.pool.max-wait-ms:10000}")
    private long maxWaitMs;

    @Value("${app.mongo.pool.max-idle-ms:60000}")
    private long maxIdleMs;

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoClientSettingsBuilderCustomizer() {
        return builder -> {
            builder.applyConnectionString(new ConnectionString(mongoUri));
            
            // 읽기/쓰기 분산: Secondary에서 읽기, Primary에서 쓰기
            // secondaryPreferred: Secondary가 사용 가능하면 Secondary에서 읽고, 없으면 Primary
            builder.readPreference(ReadPreference.secondaryPreferred());
            
            builder.applyToConnectionPoolSettings(settings -> settings
                    .maxSize(maxSize)
                    .minSize(minSize)
                    .maxWaitTime(maxWaitMs, TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(maxIdleMs, TimeUnit.MILLISECONDS));
        };
    }
}
