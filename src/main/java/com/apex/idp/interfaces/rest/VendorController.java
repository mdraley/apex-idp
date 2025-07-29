package com.apex.idp.interfaces.rest;

import com.apex.idp.application.VendorApplicationService;
import com.apex.idp.interfaces.dto.CountResponseDTO;
import com.apex.idp.interfaces.dto.VendorDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * REST controller for vendor management operations.
 * Handles vendor retrieval and count operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vendors")
@Tag(name = "Vendor Management", description = "Vendor management APIs")
@Validated
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class VendorController {

    private final VendorApplicationService vendorApplicationService;

    /**
     * Gets all vendors.
     */
    @GetMapping
    @Operation(summary = "List vendors", description = "Get list of all vendors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendors retrieved successfully")
    })
    public ResponseEntity<List<VendorDTO>> getAllVendors() {
        log.debug("Fetching all vendors");

        List<VendorDTO> vendors = vendorApplicationService.getAllVendors();
        return ResponseEntity.ok(vendors);
    }

    /**
     * Gets a specific vendor by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get vendor", description = "Get vendor details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor found"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorDTO> getVendor(
            @PathVariable @Parameter(description = "Vendor ID") String id) {

        log.debug("Fetching vendor: {}", id);

        try {
            VendorDTO vendor = vendorApplicationService.getVendor(id);
            return ResponseEntity.ok(vendor);
        } catch (RuntimeException e) {
            log.error("Vendor not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gets total vendor count.
     */
    @GetMapping("/count")
    @Operation(summary = "Get vendor count", description = "Get total count of all vendors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully")
    })
    public ResponseEntity<CountResponseDTO> getVendorCount() {
        log.debug("Fetching total vendor count");

        long count = vendorApplicationService.getTotalVendorCount();
        return ResponseEntity.ok(new CountResponseDTO(count));
    }

    /**
     * Creates a new vendor.
     */
    @PostMapping
    @Operation(summary = "Create vendor", description = "Create a new vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vendor created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Vendor already exists")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorDTO> createVendor(@Valid @RequestBody CreateVendorRequest request) {
        log.info("Creating new vendor: {}", request.getName());

        try {
            // Check if vendor already exists
            if (vendorApplicationService.vendorExistsByName(request.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            VendorDTO newVendor = vendorApplicationService.createVendor(
                    request.getName(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getAddress()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(newVendor);
        } catch (Exception e) {
            log.error("Error creating vendor", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Updates vendor information.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update vendor", description = "Update vendor information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor updated successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorDTO> updateVendor(
            @PathVariable String id,
            @Valid @RequestBody UpdateVendorRequest request) {

        log.info("Updating vendor: {}", id);

        try {
            VendorDTO updatedVendor = vendorApplicationService.updateVendor(
                    id,
                    request.getEmail(),
                    request.getPhone(),
                    request.getAddress()
            );
            return ResponseEntity.ok(updatedVendor);
        } catch (RuntimeException e) {
            log.error("Vendor not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates vendor status.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update vendor status", description = "Activate or deactivate a vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorDTO> updateVendorStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {

        log.info("Updating vendor {} status to {}", id, request.getStatus());

        try {
            VendorDTO updatedVendor = vendorApplicationService.updateVendorStatus(id, request.getStatus());
            return ResponseEntity.ok(updatedVendor);
        } catch (RuntimeException e) {
            log.error("Vendor not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class CreateVendorRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Invalid email format")
        private String email;

        private String phone;
        private String address;
    }

    @Getter
    @Setter
    public static class UpdateVendorRequest {
        @Email(message = "Invalid email format")
        private String email;

        private String phone;
        private String address;
    }

    @Getter
    @Setter
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
        private String status;
    }
}