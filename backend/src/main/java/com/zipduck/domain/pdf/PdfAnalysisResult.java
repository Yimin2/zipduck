package com.zipduck.domain.pdf;

import com.zipduck.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * PdfAnalysisResult entity representing AI analysis results for a PDF
 * FR-017, FR-018, FR-019: Store extracted criteria and eligibility analysis
 * T057: PdfAnalysisResult entity
 */
@Entity
@Table(name = "pdf_analysis_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PdfAnalysisResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_document_id", nullable = false, unique = true)
    private PdfDocument pdfDocument;

    // Extracted subscription information
    @Column(length = 500)
    private String subscriptionName;

    @Column(length = 500)
    private String location;

    @Column(length = 1000)
    private String address;

    @Column(length = 100)
    private String housingType;

    // Eligibility criteria extracted by AI
    private Integer minAge;
    private Integer maxAge;
    private Long minIncome;
    private Long maxIncome;
    private Integer minHouseholdMembers;
    private Integer maxHouseholdMembers;
    private Integer maxHousingOwned;

    @Column(columnDefinition = "TEXT")
    private String specialQualifications;

    @Column(columnDefinition = "TEXT")
    private String preferenceCategories;

    // Price information
    private Long minPrice;
    private Long maxPrice;

    // Application period (if extracted)
    @Column(length = 100)
    private String applicationPeriod;

    // Match score (0-100)
    private Integer matchScore;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isEligible = false;

    // Detailed eligibility breakdown
    @Column(columnDefinition = "TEXT")
    private String eligibilityBreakdown; // JSON string with detailed analysis

    // Actionable recommendations
    @Column(columnDefinition = "TEXT")
    private String recommendations; // JSON string with recommendations

    // OCR quality indicator (FR-037, FR-038)
    @Column(length = 50)
    private String ocrQuality; // HIGH, MEDIUM, LOW

    @Column(length = 500)
    private String ocrWarning; // Warning message if OCR quality is low

    // Raw extracted text (for debugging/reprocessing)
    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String extractedText;

    // AI processing metadata
    @Column(length = 500)
    private String aiModel; // e.g., "gemini-1.5-pro"

    @Column
    private Integer processingTimeMs; // Processing time in milliseconds

    /**
     * Set PDF document reference
     */
    public void setPdfDocument(PdfDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
    }

    /**
     * Update match score and eligibility
     */
    public void updateMatchResult(Integer matchScore, Boolean isEligible) {
        this.matchScore = matchScore;
        this.isEligible = isEligible;
    }

    /**
     * Set OCR quality and warning
     */
    public void setOcrQuality(String quality, String warning) {
        this.ocrQuality = quality;
        this.ocrWarning = warning;
    }
}