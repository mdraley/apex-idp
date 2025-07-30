package com.apex.idp.infrastructure.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO implementation of the StorageService interface.
 * Handles all document storage operations using MinIO object storage.
 */
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
    public String store(MultipartFile file, String batchId) throws StorageException {
        String objectName = batchId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        return storeWithPath(file, objectName);
    }

    @Override
    public String storeWithPath(MultipartFile file, String path) throws StorageException {
        try {
            Map<String, String> userMetadata = new HashMap<>();
            userMetadata.put("originalFilename", file.getOriginalFilename());
            userMetadata.put("uploadTime", String.valueOf(System.currentTimeMillis()));

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(path)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .userMetadata(userMetadata)
                            .build()
            );

            log.info("Stored file: {} to path: {}", file.getOriginalFilename(), path);
            return path;

        } catch (Exception e) {
            log.error("Failed to store file", e);
            throw new StorageException("Failed to store file: " + file.getOriginalFilename(), e);
        }
    }

    @Override
    public InputStream retrieve(String path) throws StorageException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to retrieve file: {}", path, e);
            throw new StorageException("Failed to retrieve file: " + path, e);
        }
    }

    @Override
    public byte[] retrieveAsBytes(String path) throws StorageException {
        try (InputStream stream = retrieve(path);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to retrieve file as bytes: {}", path, e);
            throw new StorageException("Failed to retrieve file as bytes: " + path, e);
        }
    }

    @Override
    public void delete(String path) throws StorageException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(path)
                            .build()
            );
            log.info("Deleted file: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", path, e);
            throw new StorageException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public void deleteMultiple(List<String> paths) throws StorageException {
        for (String path : paths) {
            try {
                delete(path);
            } catch (StorageException e) {
                log.error("Failed to delete file in batch: {}", path);
                // Continue with other deletions
            }
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(path)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            log.error("Error checking file existence: {}", path, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence: {}", path, e);
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String path, int expiryMinutes) throws StorageException {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(defaultBucket)
                            .object(path)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", path, e);
            throw new StorageException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public List<String> listFiles(String prefix) throws StorageException {
        List<String> files = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(defaultBucket)
                            .prefix(prefix)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                files.add(item.objectName());
            }

            return files;
        } catch (Exception e) {
            log.error("Failed to list files with prefix: {}", prefix, e);
            throw new StorageException("Failed to list files", e);
        }
    }

    @Override
    public FileMetadata getMetadata(String path) throws StorageException {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(path)
                            .build()
            );

            FileMetadata metadata = new FileMetadata();
            metadata.setPath(path);
            metadata.setSize(stat.size());
            metadata.setContentType(stat.contentType());
            metadata.setLastModified(stat.lastModified().toInstant());
            metadata.setUserMetadata(stat.userMetadata());

            return metadata;
        } catch (Exception e) {
            log.error("Failed to get metadata for: {}", path, e);
            throw new StorageException("Failed to get file metadata", e);
        }
    }
}