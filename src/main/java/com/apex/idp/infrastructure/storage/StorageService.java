package com.apex.idp.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Optional;

public interface StorageService {

    String store(MultipartFile file, String batchId);

    Optional<InputStream> retrieveDocument(String bucketName, String storagePath) throws StorageException;

    void delete(String bucketName, String path) throws StorageException;

    boolean exists(String bucketName, String path) throws StorageException;

    class StorageException extends Exception {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}