package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.MerchantCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantCacheRepository extends JpaRepository<MerchantCache, Long> {

    Optional<MerchantCache> findByPatternIgnoreCase(String pattern);

    boolean existsByPatternIgnoreCase(String pattern);
}
