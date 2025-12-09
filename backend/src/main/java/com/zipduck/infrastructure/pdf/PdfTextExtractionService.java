package com.zipduck.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * PDF Text Extraction Service using Apache PDFBox
 * Extracts text from text-based PDFs and assesses extraction quality
 */
@Slf4j
@Component
public class PdfTextExtractionService {

    private static final int MIN_TEXT_LENGTH_FOR_SUCCESS = 100;
    private static final int LOW_QUALITY_WARNING_THRESHOLD = 300;

    /**
     * Extract text from PDF file using Apache PDFBox
     * Returns extraction result with quality indicators
     *
     * @param filePath Path to the PDF file
     * @return PdfTextExtractionResult with extracted text and quality assessment
     */
    public PdfTextExtractionResult extractText(String filePath) {
        log.info("Attempting to extract text from PDF: {}", filePath);

        PdfTextExtractionResult result = new PdfTextExtractionResult();
        result.filePath = filePath;

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            // Check if PDF is encrypted
            if (document.isEncrypted()) {
                log.warn("PDF is encrypted, attempting to decrypt with empty password");
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (Exception e) {
                    result.success = false;
                    result.warning = "PDF가 암호화되어 있어 텍스트를 추출할 수 없습니다.";
                    result.extractedText = "";
                    return result;
                }
            }

            result.pageCount = document.getNumberOfPages();

            // Extract text using PDFTextStripper
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // Preserve reading order
            String text = stripper.getText(document);

            result.extractedText = text != null ? text.trim() : "";
            result.textLength = result.extractedText.length();

            // Assess extraction quality
            assessQuality(result);

            log.info("Text extraction completed: {} chars extracted from {} pages (quality: {})",
                    result.textLength, result.pageCount, result.quality);

            return result;

        } catch (IOException e) {
            log.error("Failed to extract text from PDF: {}", filePath, e);
            result.success = false;
            result.warning = "PDF 파일을 읽을 수 없습니다: " + e.getMessage();
            result.extractedText = "";
            return result;
        }
    }

    /**
     * Assess quality of extracted text
     * Determines if text extraction is sufficient or if OCR fallback is needed
     */
    private void assessQuality(PdfTextExtractionResult result) {
        if (result.textLength == 0) {
            result.success = false;
            result.isTextBased = false;
            result.quality = QualityLevel.FAILED;
            result.warning = "텍스트를 추출할 수 없습니다. 이미지 기반 PDF이거나 손상된 파일일 수 있습니다.";
            return;
        }

        if (result.textLength < MIN_TEXT_LENGTH_FOR_SUCCESS) {
            result.success = false;
            result.isTextBased = false;
            result.quality = QualityLevel.TOO_SHORT;
            result.warning = String.format("추출된 텍스트가 너무 짧습니다 (%d자). OCR을 사용합니다.", result.textLength);
            return;
        }

        // Check Korean text ratio
        int koreanChars = countKoreanCharacters(result.extractedText);
        double koreanRatio = (double) koreanChars / result.textLength;

        // Check numeric content
        int digitChars = countDigits(result.extractedText);

        result.koreanCharCount = koreanChars;
        result.digitCount = digitChars;
        result.success = true;
        result.isTextBased = true;

        // Quality assessment
        if (result.textLength < LOW_QUALITY_WARNING_THRESHOLD) {
            result.quality = QualityLevel.LOW;
            result.warning = String.format("추출된 텍스트가 짧습니다 (%d자). 일부 내용이 누락되었을 수 있습니다.", result.textLength);
        } else if (koreanRatio < 0.1 && digitChars < 10) {
            result.quality = QualityLevel.MEDIUM;
            result.warning = "한글 텍스트가 적습니다. 문서 형식이 올바른지 확인하세요.";
        } else if (digitChars < 5) {
            result.quality = QualityLevel.MEDIUM;
            result.warning = "숫자 정보가 부족합니다. 자격 조건이 불완전할 수 있습니다.";
        } else {
            result.quality = QualityLevel.HIGH;
            result.warning = null;
        }
    }

    /**
     * Count Korean characters in text
     */
    private int countKoreanCharacters(String text) {
        return (int) text.chars()
                .filter(c -> (c >= 0xAC00 && c <= 0xD7A3))
                .count();
    }

    /**
     * Count digit characters in text
     */
    private int countDigits(String text) {
        return (int) text.chars()
                .filter(Character::isDigit)
                .count();
    }

    /**
     * Result of PDF text extraction
     */
    public static class PdfTextExtractionResult {
        public String filePath;
        public boolean success;
        public boolean isTextBased;
        public String extractedText;
        public int textLength;
        public int pageCount;
        public int koreanCharCount;
        public int digitCount;
        public QualityLevel quality;
        public String warning;
    }

    /**
     * Quality levels for extracted text
     */
    public enum QualityLevel {
        HIGH,       // Sufficient text with good content
        MEDIUM,     // Sufficient length but low Korean/digit content
        LOW,        // Below warning threshold but above minimum
        TOO_SHORT,  // Below minimum threshold, OCR fallback needed
        FAILED      // No text extracted
    }
}
