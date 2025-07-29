package com.apex.idp.application;

import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import com.apex.idp.domain.vendor.VendorService;
import com.apex.idp.domain.vendor.VendorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorApplicationService {

    private final VendorRepository vendorRepository;
    private final VendorService vendorService;

    @Transactional(readOnly = true)
    public List<VendorDTO> getAllVendors() {
        return vendorRepository.findAll().stream()
                .map(VendorDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getTotalVendorCount() {
        return vendorRepository.countAllVendors();
    }

    @Transactional(readOnly = true)
    public VendorDTO getVendor(String id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
        return VendorDTO.from(vendor);
    }

    @Transactional(readOnly = true)
    public boolean vendorExistsByName(String name) {
        return vendorRepository.findByName(name).isPresent();
    }

    public VendorDTO createVendor(String name, String email, String phone, String address) {
        Vendor vendor = vendorService.createOrUpdateVendor(name, email, phone, address);
        return VendorDTO.from(vendor);
    }

    public VendorDTO updateVendor(String id, String email, String phone, String address) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.updateContactInfo(email, phone, address);
        Vendor savedVendor = vendorRepository.save(vendor);
        return VendorDTO.from(savedVendor);
    }

    public VendorDTO updateVendorStatus(String id, String status) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        VendorStatus vendorStatus = VendorStatus.valueOf(status.toUpperCase());
        if (vendorStatus == VendorStatus.ACTIVE) {
            vendor.activate();
        } else {
            vendor.deactivate();
        }

        Vendor savedVendor = vendorRepository.save(vendor);
        return VendorDTO.from(savedVendor);
    }
}