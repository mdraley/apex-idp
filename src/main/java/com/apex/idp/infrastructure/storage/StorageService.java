package com.apex.idp.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Interface for document storage operations.
 * Implementations can use MinIO, S3, or other storage solutions.
 */
public interface StorageService {

    /**
     * Stores a file and returns the storage path.
     *
     * @param file The file to store
     * @param batchId The batch ID for organizing files
     * @return The storage path of the file
     * @throws StorageException if storage fails
     */
    String store(MultipartFile file, String batchId) throws StorageException;

    /**
     * Stores a file with custom path.
     *
     * @param file The file to store
     * @param path The custom storage path
     * @return The storage path of the file
     * @throws StorageException if storage fails
     */
    String storeWithPath(MultipartFile file, String path) throws StorageException;

    /**
     * Retrieves a file as an input stream.
     *
     * @param path The storage path
     * @return InputStream of the file
     * @throws StorageException if retrieval fails
     */
    InputStream retrieve(String path) throws StorageException;

    /**
     * Retrieves a file as byte array.
     *
     * @param path The storage path
     * @return Byte array of the file
     * @throws StorageException if retrieval fails
     */
    byte[] retrieveAsBytes(String path) throws StorageException;

    /**
     * Deletes a file from storage.
     *
     * @param path The storage path
     * @throws StorageException if deletion fails
     */
    void delete(String path) throws StorageException;

    /**
     * Deletes multiple files from storage.
     *
     * @param paths List of storage paths
     * @throws StorageException if deletion fails
     */
    void deleteMultiple(List<String> paths) throws StorageException;

    /**
     * Checks if a file exists.
     *
     * @param path The storage path
     * @return true if file exists, false otherwise
     */
    boolean exists(String path);

    /**
     * Gets a presigned URL for direct file access.
     *
     * @param path The storage path
     * @param expiryMinutes URL expiry time in minutes
     * @return Presigned URL
     * @throws StorageException if URL generation fails
     */
    String getPresignedUrl(String path, int expiryMinutes) throws StorageException;

    /**
     * Lists all files in a given prefix/folder.
     *
     * @param prefix The folder prefix
     * @return List of file paths
     * @throws StorageException if listing fails
     */
    List<String> listFiles(String prefix) throws StorageException;

    /**
     * Gets file metadata.
     *
     * @param path The storage path
     * @return File metadata
     * @throws StorageException if metadata retrieval fails
     */
    FileMetadata getMetadata(String path) throws StorageException;

    /**
     * File metadata class
     */
    class FileMetadata {
        private String path;
        private Long size;
        private String contentType;
        private java.time.Instant lastModified;
        private java.util.Map<String, String> userMetadata;

        // Getters and setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public java.time.Instant getLastModified() { return lastModified; }
        public void setLastModified(java.time.Instant lastModified) { this.lastModified = lastModified; }

        public java.util.Map<String, String> getUserMetadata() { return userMetadata; }
        public void setUserMetadata(java.util.Map<String, String> userMetadata) { this.userMetadata = userMetadata; }
    }
}

/**
 * Custom exception for storage operations.
 */
class StorageException extends Exception {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}