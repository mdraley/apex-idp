package com.apex.idp.application.service;

import com.apex.idp.domain.vendor.DomainVendorService;
import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import com.apex.idp.domain.vendor.VendorStatus;
import com.apex.idp.interfaces.dto.VendorDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service for vendor management operations.
 * Handles vendor CRUD operations and business logic orchestration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VendorService {

    private final VendorRepository vendorRepository;
    private final DomainVendorService domainVendorService;

    /**
     * Get all vendors
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "vendors", key = "'all'")
    public List<VendorDTO> getAllVendors() {
        log.debug("Fetching all vendors");
        return vendorRepository.findAll().stream()
                .map(VendorDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get vendors with pagination
     */
    @Transactional(readOnly = true)
    public Page<VendorDTO> getVendors(Pageable pageable) {
        log.debug("Fetching vendors with pagination: {}", pageable);
        return vendorRepository.findAll(pageable)
                .map(VendorDTO::from);
    }

    /**
     * Get vendor by ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "vendors", key = "#id")
    public VendorDTO getVendor(String id) {
        Vendor vendor = getVendorById(id);
        return VendorDTO.from(vendor);
    }

    /**
     * Get vendor entity by ID
     */
    @Transactional(readOnly = true)
    public Vendor getVendorById(String id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
    }

    /**
     * Get vendor by name
     */
    @Transactional(readOnly = true)
    public Optional<VendorDTO> getVendorByName(String name) {
        return vendorRepository.findByNameIgnoreCase(name)
                .map(VendorDTO::from);
    }

    /**
     * Get total vendor count
     */
    @Transactional(readOnly = true)
    public long getTotalVendorCount() {
        return vendorRepository.countAllVendors();
    }

    /**
     * Get active vendor count
     */
    @Transactional(readOnly = true)
    public long getActiveVendorCount() {
        return vendorRepository.findByStatus(VendorStatus.ACTIVE).size();
    }

    /**
     * Create new vendor
     */
    public Vendor createVendor(String name, String email, String phone, String address) {
        log.info("Creating new vendor: {}", name);

        // Check if vendor already exists
        Optional<Vendor> existingVendor = vendorRepository.findByNameIgnoreCase(name);
        if (existingVendor.isPresent()) {
            log.warn("Vendor already exists: {}", name);
            return existingVendor.get();
        }

        Vendor vendor = Vendor.create(name);
        if (email != null || phone != null || address != null) {
            vendor.updateContactInfo(email, phone, address);
        }

        Vendor savedVendor = vendorRepository.save(vendor);
        log.info("Created vendor: {} with ID: {}", name, savedVendor.getId());

        return savedVendor;
    }

    /**
     * Update vendor information
     */
    public VendorDTO updateVendor(String id, String email, String phone, String address) {
        log.info("Updating vendor: {}", id);

        Vendor vendor = getVendorById(id);
        vendor.updateContactInfo(email, phone, address);

        Vendor savedVendor = vendorRepository.save(vendor);
        log.info("Updated vendor: {}", id);

        return VendorDTO.from(savedVendor);
    }

    /**
     * Update vendor status
     */
    public VendorDTO updateVendorStatus(String id, String status) {
        log.info("Updating vendor {} status to {}", id, status);

        Vendor vendor = getVendorById(id);
        VendorStatus vendorStatus = VendorStatus.valueOf(status.toUpperCase());

        if (vendorStatus == VendorStatus.ACTIVE) {
            vendor.activate();
        } else {
            vendor.deactivate();
        }

        Vendor savedVendor = vendorRepository.save(vendor);
        log.info("Updated vendor {} status to {}", id, vendorStatus);

        return VendorDTO.from(savedVendor);
    }

    /**
     * Search vendors by name
     */
    @Transactional(readOnly = true)
    public List<VendorDTO> searchVendorsByName(String nameQuery) {
        log.debug("Searching vendors with name containing: {}", nameQuery);

        return vendorRepository.findByNameContainingIgnoreCase(nameQuery).stream()
                .map(VendorDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get vendors by status
     */
    @Transactional(readOnly = true)
    public List<VendorDTO> getVendorsByStatus(VendorStatus status) {
        log.debug("Fetching vendors with status: {}", status);

        return vendorRepository.findByStatus(status).stream()
                .map(VendorDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Check if vendor exists by name
     */
    @Transactional(readOnly = true)
    public boolean vendorExistsByName(String name) {
        return vendorRepository.findByNameIgnoreCase(name).isPresent();
    }

    /**
     * Delete vendor (soft delete by deactivating)
     */
    public void deleteVendor(String id) {
        log.info("Soft deleting vendor: {}", id);

        Vendor vendor = getVendorById(id);
        vendor.deactivate();
        vendorRepository.save(vendor);

        log.info("Vendor {} deactivated", id);
    }
}