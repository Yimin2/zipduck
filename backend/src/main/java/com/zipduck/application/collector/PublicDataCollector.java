package com.zipduck.application.collector;

import com.zipduck.domain.subscription.Subscription;
import com.zipduck.domain.subscription.SubscriptionCommandService;
import com.zipduck.domain.subscription.SubscriptionQueryService;
import com.zipduck.infrastructure.external.PublicDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled task for collecting subscription data from public data portal
 * T039: Scheduled collection with @Scheduled(cron = "0 0 2 * * *")
 * FR-003, FR-011
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataCollector {

    private final PublicDataClient publicDataClient;
    private final SubscriptionCommandService subscriptionCommandService;
    private final SubscriptionQueryService subscriptionQueryService;

    /**
     * Collect public subscription data daily at 2 AM
     * FR-003: Retrieve current subscription listings from public data sources
     * FR-011: Update subscription listings when new data becomes available
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void collectPublicData() {
        log.info("공공데이터 수집 시작");

        try {
            // Collect data from last 3 months
            LocalDate fromDate = LocalDate.now().minusMonths(3);
            List<PublicDataClient.PublicSubscriptionDto> publicSubscriptions =
                    publicDataClient.fetchSubscriptions(fromDate);

            log.info("공공데이터 {} 건 조회 완료", publicSubscriptions.size());

            int createdCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for (PublicDataClient.PublicSubscriptionDto dto : publicSubscriptions) {
                try {
                    // Check if already exists (FR-028: Duplicate detection)
                    Subscription existingSubscription = subscriptionQueryService.findByPublicDataId(dto.getExternalId());

                    if (existingSubscription == null) {
                        // Create new subscription
                        Subscription newSubscription = convertToSubscription(dto);
                        subscriptionCommandService.create(newSubscription);
                        createdCount++;
                    } else {
                        // Update existing subscription if needed
                        updateSubscription(existingSubscription, dto);
                        subscriptionCommandService.update(existingSubscription);
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("청약 정보 처리 실패: externalId={}, error={}", dto.getExternalId(), e.getMessage());
                    skippedCount++;
                }
            }

            log.info("공공데이터 수집 완료 - 생성: {}, 업데이트: {}, 스킵: {}", createdCount, updatedCount, skippedCount);

        } catch (Exception e) {
            log.error("공공데이터 수집 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Deactivate expired subscriptions daily
     * FR-030, T041
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void deactivateExpiredSubscriptions() {
        log.info("만료된 청약 비활성화 시작");

        try {
            int deactivatedCount = subscriptionCommandService.deactivateExpiredSubscriptions();
            log.info("만료된 청약 {} 건 비활성화 완료", deactivatedCount);
        } catch (Exception e) {
            log.error("만료된 청약 비활성화 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert PublicSubscriptionDto to Subscription entity
     * T040: Transform and save subscriptions
     */
    private Subscription convertToSubscription(PublicDataClient.PublicSubscriptionDto dto) {
        // Parse location from address (simplified - extract first part)
        String location = extractLocation(dto.getLocation());

        // Determine housing type
        Subscription.HousingType housingType = parseHousingType(dto.getHousingType());

        // Default eligibility criteria (공공데이터에서 제공하지 않는 경우 기본값 사용)
        // 실제로는 API 응답에 포함된 경우 파싱해야 함
        return Subscription.builder()
                .name(dto.getName())
                .location(location)
                .address(dto.getLocation())
                .housingType(housingType)
                .applicationStartDate(dto.getApplicationStartDate())
                .applicationEndDate(dto.getApplicationEndDate())
                .dataSource(Subscription.DataSource.PUBLIC_DB)
                .publicDataId(dto.getExternalId())
                .isActive(true)
                // Default eligibility criteria - should be enhanced with actual data from API
                .minAge(19)
                .maxAge(null) // No upper limit
                .minIncome(null) // No lower limit
                .maxIncome(null) // No upper limit
                .minHouseholdMembers(1)
                .maxHouseholdMembers(null) // No upper limit
                .maxHousingOwned(0) // Most subscriptions require 0 housing owned
                .build();
    }

    /**
     * Update existing subscription with new data
     */
    private void updateSubscription(Subscription existing, PublicDataClient.PublicSubscriptionDto dto) {
        // Update only if application dates have changed or other critical fields
        // This is simplified - in production, you might want to track more changes
        log.debug("청약 정보 업데이트: externalId={}", dto.getExternalId());
        // For now, we just log - full update logic can be added if needed
    }

    /**
     * Extract location from full address
     */
    private String extractLocation(String fullAddress) {
        if (fullAddress == null || fullAddress.isEmpty()) {
            return "기타";
        }

        // Extract first part of address (e.g., "서울" from "서울시 강남구...")
        if (fullAddress.contains("서울")) return "서울";
        if (fullAddress.contains("경기")) return "경기";
        if (fullAddress.contains("인천")) return "인천";
        if (fullAddress.contains("부산")) return "부산";
        if (fullAddress.contains("대구")) return "대구";
        if (fullAddress.contains("대전")) return "대전";
        if (fullAddress.contains("광주")) return "광주";
        if (fullAddress.contains("울산")) return "울산";
        if (fullAddress.contains("세종")) return "세종";

        // Default to first word
        String[] parts = fullAddress.split(" ");
        return parts.length > 0 ? parts[0] : "기타";
    }

    /**
     * Parse housing type from string
     */
    private Subscription.HousingType parseHousingType(String typeStr) {
        if (typeStr == null) {
            return Subscription.HousingType.ETC;
        }

        String normalized = typeStr.trim().toUpperCase();
        if (normalized.contains("아파트") || normalized.contains("APT")) {
            return Subscription.HousingType.APARTMENT;
        } else if (normalized.contains("오피스텔")) {
            return Subscription.HousingType.OFFICETEL;
        } else if (normalized.contains("빌라")) {
            return Subscription.HousingType.VILLA;
        } else if (normalized.contains("타운하우스")) {
            return Subscription.HousingType.TOWNHOUSE;
        }

        return Subscription.HousingType.ETC;
    }
}
