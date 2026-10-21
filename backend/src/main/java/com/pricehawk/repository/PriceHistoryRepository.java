package com.pricehawk.repository;

import com.pricehawk.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByItemSourceTrackedItemIdOrderByRecordedAtAsc(Long trackedItemId);

    @Modifying
    @Query("DELETE FROM PriceHistory h WHERE h.itemSource.id IN (SELECT s.id FROM ItemSource s WHERE s.trackedItem.id = :trackedItemId)")
    void deleteByItemSourceTrackedItemId(@Param("trackedItemId") Long trackedItemId);
}

