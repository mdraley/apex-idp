package com.apex.idp.application.service;

import com.apex.idp.domain.vendor.VendorRepository;
import com.apex.idp.interfaces.dto.VendorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;

    public List<VendorDTO> getAllVendors() {
        return vendorRepository.findAll().stream()
                .map(VendorDTO::from)
                .collect(Collectors.toList());
    }

    public long getTotalVendorCount() {
        return vendorRepository.countAllVendors();
    }
}
