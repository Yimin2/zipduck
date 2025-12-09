package com.zipduck.application.ai;

import com.zipduck.infrastructure.external.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for AI-powered subscription criteria extraction using Gemini
 * FR-017: Use Gemini AI to extract eligibility criteria from PDF text
 * T063: GeminiService implementation with prompt engineering
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final GeminiClient geminiClient;

    /**
     * Extract subscription eligibility criteria from PDF text using Gemini AI
     * FR-017: AI-powered criteria extraction
     * T063: Prompt engineering for accurate extraction
     */
    public SubscriptionCriteria extractCriteria(String pdfText) {
        log.info("Extracting subscription criteria using Gemini AI");

        // Validate input text
        if (pdfText == null || pdfText.trim().isEmpty()) {
            log.error("Cannot extract criteria from empty text");
            throw new IllegalArgumentException("PDF text is empty. Cannot extract criteria.");
        }

        if (pdfText.trim().length() < 50) {
            log.warn("PDF text is very short ({} chars), extraction may be unreliable", pdfText.length());
        }

        String prompt = buildExtractionPrompt(pdfText);

        try {
            // temperature=0.2 for more deterministic output, maxTokens=2000 for detailed extraction
            String response = geminiClient.generateContent(prompt, 0.2, 2000);
            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Failed to extract criteria with Gemini: {}", e.getMessage(), e);
            throw new RuntimeException("AI criteria extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build prompt for Gemini to extract subscription criteria
     * T063: Prompt engineering
     */
    private String buildExtractionPrompt(String pdfText) {
        return """
                다음은 주택 청약 공고문의 내용입니다. 이 문서에서 자격 조건을 추출해주세요.

                **추출할 정보:**
                1. 청약명 (분양 단지명) - **필수**
                2. 위치/지역 - **필수** (예: 서울, 경기, 부산 등)
                3. 주소
                4. 주택 유형 (아파트, 오피스텔, 빌라 등)
                5. 나이 제한 (최소 나이, 최대 나이)
                6. 소득 기준 (최소 소득, 최대 소득, KRW 단위)
                7. 세대원 수 조건 (최소, 최대)
                8. 무주택 조건 (보유 가능한 주택 수)
                9. 특별 자격 조건
                10. 우대 카테고리
                11. 가격 범위 (최저가, 최고가)
                12. 청약 기간

                **출력 형식 (JSON):**
                ```json
                {
          "subscriptionName": "청약명",
                  "location": "지역 (서울, 경기 등)",
                  "address": "상세 주소",
                  "housingType": "아파트 또는 오피스텔 또는 빌라 또는 타운하우스 또는 기타",
                  "minAge": 나이최소값,
                  "maxAge": 나이최대값,
                  "minIncome": 소득최소값,
                  "maxIncome": 소득최대값,
                  "minHouseholdMembers": 세대원수최소값,
                  "maxHouseholdMembers": 세대원수최대값,
                  "maxHousingOwned": 보유가능주택수,
                  "specialQualifications": "특별 자격 조건",
                  "preferenceCategories": "우대 카테고리",
                  "minPrice": 최저가,
                  "maxPrice": 최고가,
                  "applicationPeriod": "청약 기간"
                }
                ```

                **주의사항:**
                - subscriptionName과 location은 **반드시** 추출해야 합니다
                - 명확하지 않은 항목은 null로 표시
                - 숫자는 반드시 숫자 타입으로
                - 소득과 가격은 원(KRW) 단위로 변환
                - JSON 형식을 정확히 준수

                **문서 내용:**
                """ + pdfText + """

                위 내용을 분석하여 JSON 형식으로만 답변해주세요. 다른 설명은 포함하지 마세요.
                """;
    }

    /**
     * Parse Gemini response into SubscriptionCriteria
     */
    private SubscriptionCriteria parseGeminiResponse(String response) {
        // Remove markdown code blocks if present
        String jsonStr = response.trim();
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        }
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        jsonStr = jsonStr.trim();

        try {
            // Simple JSON parsing (in production, use Jackson ObjectMapper)
            SubscriptionCriteria criteria = parseJsonManually(jsonStr);

            // Validate and apply defaults for required fields
            validateAndApplyDefaults(criteria);

            return criteria;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", response, e);
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    /**
     * Manual JSON parsing (simplified - in production use ObjectMapper)
     */
    private SubscriptionCriteria parseJsonManually(String json) {
        SubscriptionCriteria criteria = new SubscriptionCriteria();

        // Extract string fields
        criteria.subscriptionName = extractJsonString(json, "subscriptionName");
        criteria.location = extractJsonString(json, "location");
        criteria.address = extractJsonString(json, "address");
        criteria.housingType = extractJsonString(json, "housingType");
        criteria.specialQualifications = extractJsonString(json, "specialQualifications");
        criteria.preferenceCategories = extractJsonString(json, "preferenceCategories");
        criteria.applicationPeriod = extractJsonString(json, "applicationPeriod");

        // Extract numeric fields
        criteria.minAge = extractJsonInteger(json, "minAge");
        criteria.maxAge = extractJsonInteger(json, "maxAge");
        criteria.minIncome = extractJsonLong(json, "minIncome");
        criteria.maxIncome = extractJsonLong(json, "maxIncome");
        criteria.minHouseholdMembers = extractJsonInteger(json, "minHouseholdMembers");
        criteria.maxHouseholdMembers = extractJsonInteger(json, "maxHouseholdMembers");
        criteria.maxHousingOwned = extractJsonInteger(json, "maxHousingOwned");
        criteria.minPrice = extractJsonLong(json, "minPrice");
        criteria.maxPrice = extractJsonLong(json, "maxPrice");

        return criteria;
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Integer extractJsonInteger(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /**
     * Validate extracted criteria and apply sensible defaults
     * Prevents database NOT NULL constraint violations
     */
    private void validateAndApplyDefaults(SubscriptionCriteria criteria) {
        // Required field: subscriptionName
        if (criteria.subscriptionName == null || criteria.subscriptionName.trim().isEmpty()) {
            log.warn("Gemini did not extract subscription name, using default");
            criteria.subscriptionName = "알 수 없는 청약";
        }

        // Required field: location (NOT NULL in subscriptions table)
        if (criteria.location == null || criteria.location.trim().isEmpty()) {
            log.warn("Gemini did not extract location, using default");
            criteria.location = "미지정";
        }

        // Required field: housingType
        if (criteria.housingType == null || criteria.housingType.trim().isEmpty()) {
            log.warn("Gemini did not extract housing type, using default");
            criteria.housingType = "기타";
        }

        // Log validation warnings for missing optional fields
        int nullFields = 0;
        if (criteria.minAge == null && criteria.maxAge == null) nullFields++;
        if (criteria.minIncome == null && criteria.maxIncome == null) nullFields++;
        if (criteria.minPrice == null && criteria.maxPrice == null) nullFields++;

        if (nullFields > 0) {
            log.warn("Gemini extraction incomplete: {} criteria groups missing", nullFields);
        }
    }

    /**
     * Data class for extracted subscription criteria
     */
    public static class SubscriptionCriteria {
        public String subscriptionName;
        public String location;
        public String address;
        public String housingType;
        public Integer minAge;
        public Integer maxAge;
        public Long minIncome;
        public Long maxIncome;
        public Integer minHouseholdMembers;
        public Integer maxHouseholdMembers;
        public Integer maxHousingOwned;
        public String specialQualifications;
        public String preferenceCategories;
        public Long minPrice;
        public Long maxPrice;
        public String applicationPeriod;
    }
}
