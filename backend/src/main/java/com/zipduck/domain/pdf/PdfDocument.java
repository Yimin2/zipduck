package com.zipduck.domain.pdf;

import com.zipduck.domain.BaseEntity;
import com.zipduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * PdfDocument entity representing an uploaded PDF file
 * FR-016: PDF upload and tracking
 * T056: PdfDocument entity with processing status enum
 */
@Entity
@Table(name = "pdf_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PdfDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String fileName; // Original file name

    @Column(nullable = false, length = 1000)
    private String filePath; // Stored file path

    @Column(nullable = false)
    private Long fileSize; // File size in bytes

    @Column(nullable = false, length = 100)
    private String contentType; // MIME type (application/pdf, image/jpeg, etc.)

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @Column(length = 64)
    private String cacheKey; // SHA-256 hash for duplicate detection

    @Column(length = 2000)
    private String errorMessage; // Error message if processing failed

    @OneToOne(mappedBy = "pdfDocument", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private PdfAnalysisResult analysisResult; // Analysis result (if completed)

    /**
     * Processing status for PDF analysis
     * FR-016: Track processing lifecycle
     */
    public enum ProcessingStatus {
        PENDING("대기중"),
        PROCESSING("처리중"),
        COMPLETED("완료"),
        FAILED("실패");

        private final String koreanName;

        ProcessingStatus(String koreanName) {
            this.koreanName = koreanName;
        }

        public String getKoreanName() {
            return koreanName;
        }
    }

    /**
     * Update processing status
     */
    public void updateStatus(ProcessingStatus status) {
        this.status = status;
    }

    /**
     * Mark as processing
     */
    public void markAsProcessing() {
        this.status = ProcessingStatus.PROCESSING;
    }

    /**
     * Mark as completed
     */
    public void markAsCompleted() {
        this.status = ProcessingStatus.COMPLETED;
    }

    /**
     * Mark as failed with error message
     */
    public void markAsFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Set cache key (SHA-256 hash)
     */
    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    /**
     * Set user reference
     */
    public void setUser(User user) {
        this.user = user;
    }
}