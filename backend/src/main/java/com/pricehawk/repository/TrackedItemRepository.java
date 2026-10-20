package com.pricehawk.repository;

import com.pricehawk.model.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedItemRepository extends JpaRepository<TrackedItem, Long> {

    List<TrackedItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TrackedItem> findByIdAndUserId(Long id, Long userId);
}
