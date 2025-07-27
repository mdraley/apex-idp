package com.apex.idp.domain.invoice;

import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;

    public void assignVendor(Invoice invoice, String vendorName) {
        Optional<Vendor> existingVendor = vendorRepository.findByName(vendorName);

        Vendor vendor = existingVendor.orElseGet(() -> {
            Vendor newVendor = Vendor.create(vendorName);
            return vendorRepository.save(newVendor);
        });

        invoice.setVendor(vendor);
        vendor.incrementInvoiceCount();
    }
}
