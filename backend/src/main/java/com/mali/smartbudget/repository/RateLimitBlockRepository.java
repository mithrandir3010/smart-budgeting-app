package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.RateLimitBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RateLimitBlockRepository extends JpaRepository<RateLimitBlock, String> {

    @Query("SELECT b FROM RateLimitBlock b WHERE b.bucketKey = :key AND b.blockedUntil > :now")
    Optional<RateLimitBlock> findActiveBlock(@Param("key") String key, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RateLimitBlock b WHERE b.blockedUntil < :now")
    void deleteExpired(@Param("now") Instant now);
}
