package com.pawanputra.bos.platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Port for binary object storage. Feature modules depend on this interface only; the database stores
 * the object key and metadata, never the bytes.
 */
public interface ObjectStorage {

    /**
     * @param key           storage key, e.g. {@code documents/2026/10/<uuid>.pdf}
     * @param contentLength exact number of bytes in {@code content}
     */
    void put(String key, InputStream content, long contentLength, String contentType);

    /** The caller must close the returned stream. */
    InputStream get(String key);

    boolean exists(String key);

    /** Idempotent: deleting a missing key succeeds. */
    void delete(String key);

    /** Time-limited URL a browser can use to download the object directly from storage. */
    URI presignedDownloadUrl(String key, Duration validFor);
}
