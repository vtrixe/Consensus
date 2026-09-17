package com.example.consensus.analytics;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportStorageService {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MinioClient minioClient;
    private final String minioBucket;

    public String upload(String filename, byte[] bytes) throws Exception {
        String key = UUID.randomUUID() + "/" + filename;
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioBucket)
                .object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(CONTENT_TYPE)
                .build());
        return key;
    }

    public byte[] download(String key) throws Exception {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(minioBucket).object(key).build())) {
            return is.readAllBytes();
        }
    }
}
