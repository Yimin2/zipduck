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
                    .queryParam("page", 1)
                    .queryParam("perPage", DEFAULT_PAGE_SIZE)
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

            // ODCloud API 응답 구조: { "data": [...], "currentCount": 10, "totalCount": 2604 }
            JsonNode dataNode = rootNode.path("data");

            List<PublicSubscriptionDto> subscriptions = new ArrayList<>();

            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    subscriptions.add(parseSubscriptionItem(item));
                }
            } else if (!dataNode.isMissingNode()) {
                subscriptions.add(parseSubscriptionItem(dataNode));
            }

            log.info("청약 정보 {} 건 파싱 완료 (전체: {}건)", subscriptions.size(), rootNode.path("totalCount").asInt(0));
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
            // 기본 정보
            .externalId(getTextValue(item, "PBLANC_NO"))
            .houseManageNo(getTextValue(item, "HOUSE_MANAGE_NO"))
            .name(getTextValue(item, "HOUSE_NM"))
            .location(getTextValue(item, "HSSPLY_ADRES"))
            .zipCode(getTextValue(item, "HSSPLY_ZIP"))
            .housingType(getTextValue(item, "HOUSE_SECD_NM"))
            .housingDetailType(getTextValue(item, "HOUSE_DTL_SECD_NM"))
            .rentType(getTextValue(item, "RENT_SECD_NM"))
            .supplyCount(getIntValue(item, "TOT_SUPLY_HSHLDCO"))

            // 청약 일정
            .announcementDate(parseDate(getTextValue(item, "RCRIT_PBLANC_DE")))
            .applicationStartDate(parseDate(getTextValue(item, "RCEPT_BGNDE")))
            .applicationEndDate(parseDate(getTextValue(item, "RCEPT_ENDDE")))
            .specialSupplyStartDate(parseDate(getTextValue(item, "SPSPLY_RCEPT_BGNDE")))
            .specialSupplyEndDate(parseDate(getTextValue(item, "SPSPLY_RCEPT_ENDDE")))
            .winnerAnnouncementDate(parseDate(getTextValue(item, "PRZWNER_PRESNATN_DE")))
            .contractStartDate(parseDate(getTextValue(item, "CNTRCT_CNCLS_BGNDE")))
            .contractEndDate(parseDate(getTextValue(item, "CNTRCT_CNCLS_ENDDE")))

            // 일반공급 1순위 일정
            .generalRank1AreaStartDate(parseDate(getTextValue(item, "GNRL_RNK1_CRSPAREA_RCPTDE")))
            .generalRank1AreaEndDate(parseDate(getTextValue(item, "GNRL_RNK1_CRSPAREA_ENDDE")))
            .generalRank1EtcAreaStartDate(parseDate(getTextValue(item, "GNRL_RNK1_ETC_AREA_RCPTDE")))
            .generalRank1EtcAreaEndDate(parseDate(getTextValue(item, "GNRL_RNK1_ETC_AREA_ENDDE")))
            .generalRank1EtcGgStartDate(parseDate(getTextValue(item, "GNRL_RNK1_ETC_GG_RCPTDE")))
            .generalRank1EtcGgEndDate(parseDate(getTextValue(item, "GNRL_RNK1_ETC_GG_ENDDE")))

            // 일반공급 2순위 일정
            .generalRank2AreaStartDate(parseDate(getTextValue(item, "GNRL_RNK2_CRSPAREA_RCPTDE")))
            .generalRank2AreaEndDate(parseDate(getTextValue(item, "GNRL_RNK2_CRSPAREA_ENDDE")))
            .generalRank2EtcAreaStartDate(parseDate(getTextValue(item, "GNRL_RNK2_ETC_AREA_RCPTDE")))
            .generalRank2EtcAreaEndDate(parseDate(getTextValue(item, "GNRL_RNK2_ETC_AREA_ENDDE")))
            .generalRank2EtcGgStartDate(parseDate(getTextValue(item, "GNRL_RNK2_ETC_GG_RCPTDE")))
            .generalRank2EtcGgEndDate(parseDate(getTextValue(item, "GNRL_RNK2_ETC_GG_ENDDE")))

            // 사업주체 정보
            .constructorName(getTextValue(item, "BSNS_MBY_NM"))
            .builderName(getTextValue(item, "CNSTRCT_ENTRPS_NM"))

            // 연락처 및 URL
            .modelHousePhone(getTextValue(item, "MDHS_TELNO"))
            .homepageUrl(getTextValue(item, "HMPG_ADRES"))
            .announcementUrl(getTextValue(item, "PBLANC_URL"))

            // 기타 정보
            .subscriptionAreaCode(getTextValue(item, "SUBSCRPT_AREA_CODE"))
            .subscriptionAreaName(getTextValue(item, "SUBSCRPT_AREA_CODE_NM"))
            .moveInYearMonth(getTextValue(item, "MVN_PREARNGE_YM"))
            .newspaperName(getTextValue(item, "NSPRC_NM"))

            // 특성 정보 (Y/N)
            .isSpeculationArea(getBooleanValue(item, "SPECLT_RDN_EARTH_AT"))
            .isAdjustmentTargetArea(getBooleanValue(item, "MDAT_TRGET_AREA_SECD"))
            .isPublicLand(getBooleanValue(item, "PUBLIC_HOUSE_EARTH_AT"))
            .isLargeScaleLand(getBooleanValue(item, "LRSCL_BLDLND_AT"))
            .isLoanRestricted(getBooleanValue(item, "PARCPRC_ULS_AT"))
            .isReconstructionBusiness(getBooleanValue(item, "IMPRMN_BSNS_AT"))
            .isPublicHousingDistrict(getBooleanValue(item, "NPLN_PRVOPR_PUBLIC_HOUSE_AT"))
            .hasPublicHousingSpecialSupply(getBooleanValue(item, "PUBLIC_HOUSE_SPCLW_APPLC_AT"))
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

    private Boolean getBooleanValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return "Y".equalsIgnoreCase(value);
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
        // 기본 정보
        private String externalId;          // 공고번호 (PBLANC_NO)
        private String houseManageNo;       // 주택관리번호 (HOUSE_MANAGE_NO)
        private String name;                // 주택명 (HOUSE_NM)
        private String location;            // 공급위치 (HSSPLY_ADRES)
        private String zipCode;             // 우편번호 (HSSPLY_ZIP)
        private String housingType;         // 주택구분 (HOUSE_SECD_NM)
        private String housingDetailType;   // 주택상세구분 (HOUSE_DTL_SECD_NM) - 민영/국민
        private String rentType;            // 분양구분 (RENT_SECD_NM)
        private int supplyCount;            // 공급세대수 (TOT_SUPLY_HSHLDCO)

        // 청약 일정
        private LocalDate announcementDate;         // 공고일 (RCRIT_PBLANC_DE)
        private LocalDate applicationStartDate;     // 청약 시작일 (RCEPT_BGNDE)
        private LocalDate applicationEndDate;       // 청약 마감일 (RCEPT_ENDDE)
        private LocalDate specialSupplyStartDate;   // 특별공급 시작일 (SPSPLY_RCEPT_BGNDE)
        private LocalDate specialSupplyEndDate;     // 특별공급 마감일 (SPSPLY_RCEPT_ENDDE)
        private LocalDate winnerAnnouncementDate;   // 당첨자발표일 (PRZWNER_PRESNATN_DE)
        private LocalDate contractStartDate;        // 계약시작일 (CNTRCT_CNCLS_BGNDE)
        private LocalDate contractEndDate;          // 계약종료일 (CNTRCT_CNCLS_ENDDE)

        // 일반공급 1순위 일정
        private LocalDate generalRank1AreaStartDate;    // 1순위 해당지역 시작일 (GNRL_RNK1_CRSPAREA_RCPTDE)
        private LocalDate generalRank1AreaEndDate;      // 1순위 해당지역 마감일 (GNRL_RNK1_CRSPAREA_ENDDE)
        private LocalDate generalRank1EtcAreaStartDate; // 1순위 기타지역 시작일 (GNRL_RNK1_ETC_AREA_RCPTDE)
        private LocalDate generalRank1EtcAreaEndDate;   // 1순위 기타지역 마감일 (GNRL_RNK1_ETC_AREA_ENDDE)
        private LocalDate generalRank1EtcGgStartDate;   // 1순위 기타경기 시작일 (GNRL_RNK1_ETC_GG_RCPTDE)
        private LocalDate generalRank1EtcGgEndDate;     // 1순위 기타경기 마감일 (GNRL_RNK1_ETC_GG_ENDDE)

        // 일반공급 2순위 일정
        private LocalDate generalRank2AreaStartDate;    // 2순위 해당지역 시작일 (GNRL_RNK2_CRSPAREA_RCPTDE)
        private LocalDate generalRank2AreaEndDate;      // 2순위 해당지역 마감일 (GNRL_RNK2_CRSPAREA_ENDDE)
        private LocalDate generalRank2EtcAreaStartDate; // 2순위 기타지역 시작일 (GNRL_RNK2_ETC_AREA_RCPTDE)
        private LocalDate generalRank2EtcAreaEndDate;   // 2순위 기타지역 마감일 (GNRL_RNK2_ETC_AREA_ENDDE)
        private LocalDate generalRank2EtcGgStartDate;   // 2순위 기타경기 시작일 (GNRL_RNK2_ETC_GG_RCPTDE)
        private LocalDate generalRank2EtcGgEndDate;     // 2순위 기타경기 마감일 (GNRL_RNK2_ETC_GG_ENDDE)

        // 사업주체 정보
        private String constructorName;     // 시행사 (BSNS_MBY_NM)
        private String builderName;         // 건설업체 (CNSTRCT_ENTRPS_NM)

        // 연락처 및 URL
        private String modelHousePhone;     // 모델하우스 전화번호 (MDHS_TELNO)
        private String homepageUrl;         // 홈페이지 주소 (HMPG_ADRES)
        private String announcementUrl;     // 청약홈 공고 URL (PBLANC_URL)

        // 기타 정보
        private String subscriptionAreaCode;    // 청약지역코드 (SUBSCRPT_AREA_CODE)
        private String subscriptionAreaName;    // 청약지역명 (SUBSCRPT_AREA_CODE_NM)
        private String moveInYearMonth;         // 입주예정년월 (MVN_PREARNGE_YM)
        private String newspaperName;           // 신문사 (NSPRC_NM)

        // 특성 정보 (Y/N)
        private Boolean isSpeculationArea;      // 투기과열지구 (SPECLT_RDN_EARTH_AT)
        private Boolean isAdjustmentTargetArea; // 조정대상지역 (MDAT_TRGET_AREA_SECD)
        private Boolean isPublicLand;           // 공공택지 (PUBLIC_HOUSE_EARTH_AT)
        private Boolean isLargeScaleLand;       // 대규모택지 (LRSCL_BLDLND_AT)
        private Boolean isLoanRestricted;       // 분양가상한제 (PARCPRC_ULS_AT)
        private Boolean isReconstructionBusiness; // 정비사업 (IMPRMN_BSNS_AT)
        private Boolean isPublicHousingDistrict;  // 공공주택지구 (NPLN_PRVOPR_PUBLIC_HOUSE_AT)
        private Boolean hasPublicHousingSpecialSupply; // 공공주택 특별공급 (PUBLIC_HOUSE_SPCLW_APPLC_AT)
    }
}
