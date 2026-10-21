package com.pricehawk.repository;

import com.pricehawk.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PriceAlert> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM PriceAlert a WHERE a.trackedItem.id = :trackedItemId")
    void deleteByTrackedItemId(@Param("trackedItemId") Long trackedItemId);
}

