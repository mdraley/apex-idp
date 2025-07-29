package com.apex.idp.domain.vendor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DomainVendorService {

    private final VendorRepository vendorRepository;

    public Vendor createOrUpdateVendor(String name, String email, String phone, String address) {
        return vendorRepository.findByName(name)
                .map(vendor -> {
                    vendor.updateContactInfo(email, phone, address);
                    return vendor;
                })
                .orElseGet(() -> {
                    Vendor newVendor = Vendor.create(name);
                    newVendor.updateContactInfo(email, phone, address);
                    return vendorRepository.save(newVendor);
                });
    }
}
