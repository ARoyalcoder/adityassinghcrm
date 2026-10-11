package com.pawanputra.bos.platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** {@link ObjectStorage} backed by any S3-compatible service (MinIO, AWS S3, ...). */
class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        client.putObject(
                request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public InputStream get(String key) {
        return client.getObject(request -> request.bucket(bucket).key(key));
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(request -> request.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(request -> request.bucket(bucket).key(key));
    }

    @Override
    public URI presignedDownloadUrl(String key, Duration validFor) {
        try {
            return presigner.presignGetObject(presign -> presign
                            .signatureDuration(validFor)
                            .getObjectRequest(request -> request.bucket(bucket).key(key)))
                    .url()
                    .toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Storage returned an invalid presigned URL", e);
        }
    }
}
