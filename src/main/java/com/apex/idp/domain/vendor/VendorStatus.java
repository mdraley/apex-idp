package com.apex.idp.domain.vendor;


/**
 * Enum representing the possible statuses of a vendor.
 */
public enum VendorStatus {
    ACTIVE,   // Vendor is active and available for business
    INACTIVE, // Vendor is temporarily disabled
    BLOCKED,  // Vendor is blocked due to compliance issues
    PENDING   // Vendor is pending verification or approval
}
