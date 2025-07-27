package com.apex.idp.application.service;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.batch.BatchSpecification;
import com.apex.idp.domain.batch.BatchStatus;
import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.kafka.BatchEventProducer;
import com.apex.idp.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BatchService {

    private final BatchRepository batchRepository;
    private final StorageService storageService;
    private final BatchEventProducer batchEventProducer;

    public Batch createBatch(String batchName, String description, List<MultipartFile> files) {
        Batch batch = Batch.create(batchName);
        batch.setDescription(description);

        for (MultipartFile file : files) {
            String filePath = storageService.store(file, batch.getId());
            Document document = Document.create(file.getOriginalFilename(), file.getContentType(), filePath);
            batch.addDocument(document);
        }

        Batch savedBatch = batchRepository.save(batch);
        batchEventProducer.sendBatchCreatedEvent(savedBatch); // Assumes this method exists
        return savedBatch;
    }

    @Transactional(readOnly = true)
    public Page<Batch> getBatches(Pageable pageable, String status, String search) {
        return batchRepository.findAll(BatchSpecification.findByCriteria(status, search), pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Batch> getBatchById(String id) {
        return batchRepository.findById(id);
    }

    public Batch updateBatch(String id, String name, String description) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Batch not found with id: " + id));
        batch.setName(name);
        batch.setDescription(description);
        return batchRepository.save(batch);
    }

    public void deleteBatch(String id) {
        if (!batchRepository.existsById(id)) {
            throw new NoSuchElementException("Batch not found with id: " + id);
        }
        batchRepository.deleteById(id);
    }

    public void reprocessBatch(String id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Batch not found with id: " + id));

        if (batch.getStatus() != BatchStatus.FAILED) {
            throw new IllegalStateException("Only batches with status FAILED can be reprocessed.");
        }
        batch.startProcessing();
        Batch savedBatch = batchRepository.save(batch);
        batchEventProducer.sendBatchCreatedEvent(savedBatch);
    }

    // Placeholder implementation for statistics
    public Object getBatchStatistics(String period) {
        // In a real app, you would build and return the BatchStatistics DTO
        return new Object();
    }

    // Method needed by the Kafka listener
    public Batch updateBatchStatus(String batchId, String newStatus) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found"));
        batch.setStatus(BatchStatus.valueOf(newStatus));
        return batchRepository.save(batch);
    }
}