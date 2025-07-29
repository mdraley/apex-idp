package com.apex.idp.infrastructure.storage;

import io.minio.*;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class MinIOStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinIOStorageService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket-name}") String bucketName) {

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.defaultBucket = bucketName;

        initializeBucket();
    }

    private void initializeBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(defaultBucket).build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(defaultBucket).build()
                );
                log.info("Created bucket: {}", defaultBucket);
            }
        } catch (Exception e) {
            log.error("Error initializing bucket", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    @Override
    public String store(MultipartFile file, String batchId) {
        String objectName = batchId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Stored file: {} in bucket: {}", objectName, defaultBucket);
            return objectName;

        } catch (Exception e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /**
     * Simplified retrieve method that uses the default bucket
     */
    public InputStream retrieve(String storagePath) throws IOException {
        try {
            return retrieveDocument(null, storagePath).orElseThrow(
                    () -> new IOException("File not found: " + storagePath)
            );
        } catch (StorageException e) {
            throw new IOException("Failed to retrieve file", e);
        }
    }

    @Override
    public Optional<InputStream> retrieveDocument(String bucketName, String storagePath)
            throws StorageException {
        try {
            String bucket = (bucketName != null) ? bucketName : defaultBucket;

            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(storagePath)
                            .build()
            );

            return Optional.of(stream);

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return Optional.empty();
            }
            throw new StorageException("Failed to retrieve document", e);
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve document", e);
        }
    }

    @Override
    public void delete(String bucketName, String path) throws StorageException {
        try {
            String bucket = (bucketName != null) ? bucketName : defaultBucket;

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .build()
            );

        } catch (Exception e) {
            throw new StorageException("Failed to delete object", e);
        }
    }

    /**
     * Simplified delete method that uses the default bucket
     */
    public void delete(String path) {
        try {
            delete(null, path);
        } catch (StorageException e) {
            log.error("Failed to delete file: {}", path, e);
        }
    }

    @Override
    public boolean exists(String bucketName, String path) throws StorageException {
        try {
            String bucket = (bucketName != null) ? bucketName : defaultBucket;

            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .build()
            );

            return true;

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw new StorageException("Failed to check object existence", e);
        } catch (Exception e) {
            throw new StorageException("Failed to check object existence", e);
        }
    }
}