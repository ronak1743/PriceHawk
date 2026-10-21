package com.pricehawk.repository;

import com.pricehawk.model.ItemSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemSourceRepository extends JpaRepository<ItemSource, Long> {

    List<ItemSource> findByTrackedItemId(Long trackedItemId);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM ItemSource s JOIN FETCH s.trackedItem t JOIN FETCH t.user u")
    List<ItemSource> findAllActiveSourcesWithUser();
}
