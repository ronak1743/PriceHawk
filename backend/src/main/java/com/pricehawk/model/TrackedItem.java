package com.pricehawk.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tracked_items")
public class TrackedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal lastNotifiedPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal previousPrice;

    @Column(name = "notify_on_any_drop", columnDefinition = "boolean default true")
    private Boolean notifyOnAnyDrop = true;

    @OneToMany(mappedBy = "trackedItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemSource> sources = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TrackedItem() {}

    public TrackedItem(User user, String title, BigDecimal targetPrice) {
        this.user = user;
        this.title = title;
        this.targetPrice = targetPrice;
        this.notifyOnAnyDrop = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public TrackedItem(User user, String title, BigDecimal targetPrice, Boolean notifyOnAnyDrop) {
        this.user = user;
        this.title = title;
        this.targetPrice = targetPrice;
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        if (this.notifyOnAnyDrop == null) {
            this.notifyOnAnyDrop = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.notifyOnAnyDrop == null) {
            this.notifyOnAnyDrop = true;
        }
    }

    public void addSource(ItemSource source) {
        sources.add(source);
        source.setTrackedItem(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public BigDecimal getLastNotifiedPrice() {
        return lastNotifiedPrice;
    }

    public void setLastNotifiedPrice(BigDecimal lastNotifiedPrice) {
        this.lastNotifiedPrice = lastNotifiedPrice;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public void setPreviousPrice(BigDecimal previousPrice) {
        this.previousPrice = previousPrice;
    }

    public Boolean getNotifyOnAnyDrop() {
        return notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
    }

    public void setNotifyOnAnyDrop(Boolean notifyOnAnyDrop) {
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
    }

    public List<ItemSource> getSources() {
        return sources;
    }

    public void setSources(List<ItemSource> sources) {
        this.sources = sources;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
