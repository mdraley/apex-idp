package com.apex.idp.domain.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    List<Invoice> findByVendorId(String vendorId);

    List<Invoice> findByStatus(InvoiceStatus status);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.document WHERE i.document.batch.id = :batchId")
    List<Invoice> findByBatchId(String batchId);

    @Query("SELECT COUNT(i) FROM Invoice i")
    long countAllInvoices();
}
