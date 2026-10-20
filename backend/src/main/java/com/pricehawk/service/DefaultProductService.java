package com.pricehawk.service;

import com.pricehawk.dto.ItemSourceResponse;
import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.dto.TrackedItemResponse;
import com.pricehawk.exception.ResourceNotFoundException;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.Platform;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.User;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.TrackedItemRepository;
import com.pricehawk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultProductService implements ProductService {

    private final UserRepository userRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final ItemSourceRepository itemSourceRepository;

    public DefaultProductService(UserRepository userRepository,
                                 TrackedItemRepository trackedItemRepository,
                                 ItemSourceRepository itemSourceRepository) {
        this.userRepository = userRepository;
        this.trackedItemRepository = trackedItemRepository;
        this.itemSourceRepository = itemSourceRepository;
    }

    @Override
    @Transactional
    public TrackedItemResponse trackProduct(Long userId, TrackProductRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Platform platform = Platform.fromUrl(request.getUrl());

        TrackedItem trackedItem = new TrackedItem(user, request.getTitle(), request.getTargetPrice());

        ItemSource source = new ItemSource(
                trackedItem,
                platform,
                request.getUrl(),
                request.getCurrentPrice(),
                request.getCurrentPrice(),
                request.getCurrency(),
                request.getImageUrl()
        );

        trackedItem.addSource(source);

        TrackedItem savedItem = trackedItemRepository.save(trackedItem);

        return mapToResponse(savedItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedItemResponse> getTrackedProducts(Long userId) {
        return trackedItemRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTrackedProduct(Long userId, Long productId) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        trackedItemRepository.delete(item);
    }

    private TrackedItemResponse mapToResponse(TrackedItem item) {
        List<ItemSourceResponse> sourceResponses = item.getSources() != null
                ? item.getSources().stream().map(this::mapSourceToResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return new TrackedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getTargetPrice(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                sourceResponses
        );
    }

    private ItemSourceResponse mapSourceToResponse(ItemSource source) {
        return new ItemSourceResponse(
                source.getId(),
                source.getPlatform(),
                source.getUrl(),
                source.getCurrentPrice(),
                source.getOriginalPrice(),
                source.getCurrency(),
                source.getImageUrl(),
                source.isInStock(),
                source.getLastCheckedAt()
        );
    }
}
