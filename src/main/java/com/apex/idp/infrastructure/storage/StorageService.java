package com.apex.idp.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Optional;

public interface StorageService {

    String store(MultipartFile file, String batchId);

    Optional<InputStream> retrieveDocument(String bucketName, String storagePath) throws StorageException;

    void delete(String bucketName, String path) throws StorageException;

    /**
     * Default implementation of delete using the default bucket
     * This simplifies implementations that have a concept of default bucket
     */
    default void delete(String path) throws StorageException {
        delete(null, path);
    }

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