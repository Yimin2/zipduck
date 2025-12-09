package com.zipduck.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 공공데이터포털 API 클라이언트
 * 청약 정보를 공공데이터포털에서 수집합니다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDataClient {

    private static final int TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_PAGE_SIZE = 100;

    @Value("${app.public-data.base-url}")
    private String baseUrl;

    @Value("${app.public-data.api-key}")
    private String apiKey;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 청약 목록 조회
     *
     * @param fromDate 조회 시작일
     * @return 청약 정보 목록
     */
    @CircuitBreaker(name = "publicData", fallbackMethod = "fetchSubscriptionsFallback")
    @Retry(name = "publicData")
    public List<PublicSubscriptionDto> fetchSubscriptions(LocalDate fromDate) {
        log.info("공공데이터포털에서 청약 정보 조회 시작: fromDate={}", fromDate);

        WebClient webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        try {
            String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail")
                    .queryParam("serviceKey", apiKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", DEFAULT_PAGE_SIZE)
                    .queryParam("startmonth", fromDate.format(DateTimeFormatter.ofPattern("yyyyMM")))
                    .queryParam("_type", "json")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .block();

            return parseSubscriptionResponse(response);
        } catch (Exception e) {
            log.error("공공데이터포털 API 호출 실패: {}", e.getMessage(), e);
            throw new PublicDataApiException("공공데이터포털에서 청약 정보를 가져오는데 실패했습니다", e);
        }
    }

    /**
     * 특정 청약 상세 정보 조회
     *
     * @param externalId 외부 청약 ID
     * @return 청약 상세 정보
     */
    @CircuitBreaker(name = "publicData", fallbackMethod = "fetchSubscriptionDetailFallback")
    @Retry(name = "publicData")
    public PublicSubscriptionDto fetchSubscriptionDetail(String externalId) {
        log.info("청약 상세 정보 조회: externalId={}", externalId);

        WebClient webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        try {
            String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/ApplyhomeInfoDetailSvc/getAPTLttotPblancDetail")
                    .queryParam("serviceKey", apiKey)
                    .queryParam("pblancNo", externalId)
                    .queryParam("_type", "json")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .block();

            List<PublicSubscriptionDto> results = parseSubscriptionResponse(response);
            if (results.isEmpty()) {
                throw new PublicDataApiException("청약 정보를 찾을 수 없습니다: " + externalId);
            }
            return results.get(0);
        } catch (Exception e) {
            log.error("청약 상세 정보 조회 실패: {}", e.getMessage(), e);
            throw new PublicDataApiException("청약 상세 정보를 가져오는데 실패했습니다", e);
        }
    }

    /**
     * API 응답 파싱
     */
    private List<PublicSubscriptionDto> parseSubscriptionResponse(String response) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode responseNode = rootNode.path("response");
            JsonNode bodyNode = responseNode.path("body");
            JsonNode itemsNode = bodyNode.path("items");
            JsonNode itemNode = itemsNode.path("item");

            List<PublicSubscriptionDto> subscriptions = new ArrayList<>();

            if (itemNode.isArray()) {
                for (JsonNode item : itemNode) {
                    subscriptions.add(parseSubscriptionItem(item));
                }
            } else if (!itemNode.isMissingNode()) {
                subscriptions.add(parseSubscriptionItem(itemNode));
            }

            log.info("청약 정보 {} 건 파싱 완료", subscriptions.size());
            return subscriptions;
        } catch (Exception e) {
            log.error("API 응답 파싱 실패: {}", e.getMessage(), e);
            throw new PublicDataApiException("API 응답을 파싱하는데 실패했습니다", e);
        }
    }

    /**
     * 개별 청약 항목 파싱
     */
    private PublicSubscriptionDto parseSubscriptionItem(JsonNode item) {
        return PublicSubscriptionDto.builder()
            .externalId(getTextValue(item, "PBLANC_NO"))
            .name(getTextValue(item, "HOUSE_NM"))
            .location(getTextValue(item, "HSSPLY_ADRES"))
            .housingType(getTextValue(item, "HOUSE_SECD_NM"))
            .applicationStartDate(parseDate(getTextValue(item, "RCEPT_BGNDE")))
            .applicationEndDate(parseDate(getTextValue(item, "RCEPT_ENDDE")))
            .supplyCount(getIntValue(item, "TOT_SUPLY_HSHLDCO"))
            .minPrice(getLongValue(item, "LTTOT_TOP_AMOUNT"))
            .maxPrice(getLongValue(item, "LTTOT_TOP_AMOUNT"))
            .constructorName(getTextValue(item, "BSNS_MBY_NM"))
            .announcementDate(parseDate(getTextValue(item, "PBLANC_DE")))
            .build();
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    private int getIntValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asInt(0) : 0;
    }

    private long getLongValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asLong(0) : 0;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (Exception e2) {
                log.warn("날짜 파싱 실패: {}", dateStr);
                return null;
            }
        }
    }

    /**
     * Fallback: 청약 목록 조회 실패 시
     */
    private List<PublicSubscriptionDto> fetchSubscriptionsFallback(LocalDate fromDate, Exception e) {
        log.error("공공데이터포털 API Circuit Breaker 작동: {}", e.getMessage());
        return Collections.emptyList();
    }

    /**
     * Fallback: 청약 상세 조회 실패 시
     */
    private PublicSubscriptionDto fetchSubscriptionDetailFallback(String externalId, Exception e) {
        log.error("공공데이터포털 API Circuit Breaker 작동 (상세): {}", e.getMessage());
        throw new PublicDataApiException("공공데이터포털 API가 현재 사용 불가능합니다. 잠시 후 다시 시도해주세요.", e);
    }

    /**
     * 공공데이터 API 예외
     */
    public static class PublicDataApiException extends RuntimeException {
        public PublicDataApiException(String message) {
            super(message);
        }

        public PublicDataApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 공공데이터 청약 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PublicSubscriptionDto {
        private String externalId;          // 공고번호
        private String name;                // 주택명
        private String location;            // 공급위치
        private String housingType;         // 주택구분 (아파트, 오피스텔 등)
        private LocalDate applicationStartDate;  // 청약 시작일
        private LocalDate applicationEndDate;    // 청약 마감일
        private int supplyCount;            // 공급세대수
        private long minPrice;              // 최저 분양가
        private long maxPrice;              // 최고 분양가
        private String constructorName;     // 시행사
        private LocalDate announcementDate; // 공고일
    }
}
