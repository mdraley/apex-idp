package com.apex.idp.application.service;

import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public List<InvoiceDTO> getInvoicesByBatch(String batchId) {
        return invoiceRepository.findByBatchId(batchId).stream()
                .map(InvoiceDTO::from)
                .collect(Collectors.toList());
    }

    public long getTotalInvoiceCount() {
        return invoiceRepository.countAllInvoices();
    }

    public InvoiceDTO getInvoice(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return InvoiceDTO.from(invoice);
    }
}
