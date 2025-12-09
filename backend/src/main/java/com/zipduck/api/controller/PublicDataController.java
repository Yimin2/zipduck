package com.zipduck.api.controller;

import com.zipduck.infrastructure.external.PublicDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/public-data")
@RequiredArgsConstructor
public class PublicDataController {

    private final PublicDataClient publicDataClient;

    // 청약 목록 조회
    @GetMapping("/subscriptions")
    public List<PublicDataClient.PublicSubscriptionDto> getSubscriptions(
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        return publicDataClient.fetchSubscriptions(fromDate);
    }

    // 청약 상세 조회
    @GetMapping("/subscriptions/{externalId}")
    public PublicDataClient.PublicSubscriptionDto getSubscriptionDetail(@PathVariable String externalId) {
        return publicDataClient.fetchSubscriptionDetail(externalId);
    }
}
