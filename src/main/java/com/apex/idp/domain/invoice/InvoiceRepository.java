package com.apex.idp.domain.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    // Basic finder methods
    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByVendorId(String vendorId);

    List<Invoice> findByDocumentId(String documentId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    // Date-based finder methods
    List<Invoice> findByDueDateBefore(LocalDate date);

    List<Invoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    // Custom queries with JOIN FETCH
    @Query("SELECT i FROM Invoice i JOIN FETCH i.document WHERE i.document.batch.id = :batchId")
    List<Invoice> findByBatchId(String batchId);

    // Aggregate queries
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status")
    long countByStatus(InvoiceStatus status);

    @Query("SELECT COUNT(i) FROM Invoice i")
    long countAllInvoices();

    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.status = :status")
    Double sumAmountByStatus(InvoiceStatus status);
}