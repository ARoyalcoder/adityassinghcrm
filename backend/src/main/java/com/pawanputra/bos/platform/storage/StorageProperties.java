package com.pawanputra.bos.platform.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param endpoint        base URL of the S3-compatible service (MinIO locally)
 * @param pathStyleAccess {@code true} for MinIO, which serves buckets as path segments rather than subdomains
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        boolean pathStyleAccess) {
}
