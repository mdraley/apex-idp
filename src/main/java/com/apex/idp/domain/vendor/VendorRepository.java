package com.apex.idp.domain.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, String> {

    Optional<Vendor> findByName(String name);

    List<Vendor> findByStatus(VendorStatus status);

    List<Vendor> findByNameContainingIgnoreCase(String name);

    @Query("SELECT COUNT(v) FROM Vendor v")
    long countAllVendors();
}
