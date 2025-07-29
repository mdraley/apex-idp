package com.apex.idp.application;

import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.domain.invoice.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceApplicationService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public List<InvoiceDTO> getInvoicesByBatch(String batchId) {
        return invoiceRepository.findByBatchId(batchId).stream()
                .map(InvoiceDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getTotalInvoiceCount() {
        return invoiceRepository.countAllInvoices();
    }

    @Transactional(readOnly = true)
    public InvoiceDTO getInvoice(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return InvoiceDTO.from(invoice);
    }

    public InvoiceDTO updateInvoiceStatus(String id, InvoiceStatus status) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // Update the status based on the enum value
        switch (status) {
            case APPROVED:
                invoice.approve();
                break;
            case REJECTED:
                invoice.reject();
                break;
            default:
                // For other statuses, we might need to add more methods to Invoice entity
                // For now, we'll handle these cases as exceptions
                throw new IllegalArgumentException("Status update to " + status + " not supported");
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);
        return InvoiceDTO.from(savedInvoice);
    }

    public InvoiceDTO approveInvoice(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        invoice.approve();
        Invoice savedInvoice = invoiceRepository.save(invoice);
        return InvoiceDTO.from(savedInvoice);
    }

    public InvoiceDTO rejectInvoice(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        invoice.reject();
        Invoice savedInvoice = invoiceRepository.save(invoice);
        return InvoiceDTO.from(savedInvoice);
    }
}