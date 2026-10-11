package com.pawanputra.bos.support;

import com.pawanputra.bos.platform.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Object storage for tests: no MinIO is running, so uploaded bytes are kept in memory. */
@Component
@Primary
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        try {
            objects.put(key, content.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream get(String key) {
        return new ByteArrayInputStream(objects.get(key));
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public URI presignedDownloadUrl(String key, Duration validFor) {
        return URI.create("http://storage.test/" + key);
    }

    public byte[] bytes(String key) {
        return objects.get(key);
    }
}
