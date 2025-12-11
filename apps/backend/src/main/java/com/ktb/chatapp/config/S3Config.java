package com.ktb.chatapp.config;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.file", name = "storage", havingValue = "s3")
public class S3Config {

    @Value("${app.file.s3.access-key}")
    private String accessKey;

    @Value("${app.file.s3.secret-key}")
    private String secretKey;

    @Value("${app.file.s3.region}")
    private String region;

    @Value("${app.file.s3.endpoint:}")
    private String endpoint;

    @Value("${app.file.s3.path-style:true}")
    private boolean pathStyleAccess;

    @Bean
    public S3Client s3Client() {
        var credentials = AwsBasicCredentials.create(accessKey, secretKey);
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region));

        if (StringUtils.hasText(endpoint)) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }

        if (pathStyleAccess) {
            builder = builder.serviceConfiguration(
                    S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }
}
