package com.zipduck.infrastructure.external;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Vision API Client
 * Handles OCR (Optical Character Recognition) for image-based PDFs and photos
 *
 * Authentication: Uses Google Service Account credentials from a JSON file
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionClient {

    @Value("${GOOGLE_APPLICATION_CREDENTIALS:./backend/config/google-vision-credentials.json}")
    private String credentialsPath;

    /**
     * Create ImageAnnotatorClient with explicit credentials
     */
    private ImageAnnotatorClient createVisionClient() throws IOException {
        log.debug("Loading Google Vision credentials from: {}", credentialsPath);

        try (FileInputStream credentialsStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            return ImageAnnotatorClient.create(settings);
        } catch (IOException e) {
            log.error("Failed to load Google Vision credentials from: {}", credentialsPath, e);
            throw new IOException("Failed to load Vision API credentials. Please check the credentials file at: " + credentialsPath, e);
        }
    }

    /**
     * Detect if image content requires OCR processing
     *
     * @param filePath Path to the file to analyze
     * @return true if the file contains image content that needs OCR
     */
    @CircuitBreaker(name = "vision", fallbackMethod = "detectImageContentFallback")
    @Retry(name = "vision")
    public boolean detectImageContent(String filePath) {
        log.info("Detecting image content in file: {}", filePath);

        try (ImageAnnotatorClient vision = createVisionClient()) {
            ByteString imgBytes = ByteString.readFrom(Files.newInputStream(Path.of(filePath)));

            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (!responses.isEmpty()) {
                AnnotateImageResponse res = responses.get(0);
                if (res.hasError()) {
                    log.error("Vision API error: {}", res.getError().getMessage());
                    return false;
                }

                // If text is detected, it means the image contains text content
                String detectedText = res.getFullTextAnnotation().getText();
                boolean hasText = detectedText != null && !detectedText.trim().isEmpty();

                log.info("Image content detection result: hasText={}", hasText);
                return hasText;
            }

            return false;
        } catch (IOException e) {
            log.error("Error detecting image content: {}", e.getMessage(), e);
            throw new VisionApiException("Failed to detect image content", e);
        }
    }

    /**
     * Perform OCR on an image file to extract text
     *
     * @param filePath Path to the image file
     * @return Extracted text content
     */
    @CircuitBreaker(name = "vision", fallbackMethod = "performOcrFallback")
    @Retry(name = "vision")
    public String performOcr(String filePath) {
        log.info("Performing OCR on file: {}", filePath);

        try (ImageAnnotatorClient vision = createVisionClient()) {
            ByteString imgBytes = ByteString.readFrom(Files.newInputStream(Path.of(filePath)));

            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (!responses.isEmpty()) {
                AnnotateImageResponse res = responses.get(0);

                if (res.hasError()) {
                    log.error("Vision API OCR error: {}", res.getError().getMessage());
                    throw new VisionApiException("OCR processing failed: " + res.getError().getMessage());
                }

                String extractedText = res.getFullTextAnnotation().getText();

                // Check OCR quality
                if (extractedText == null || extractedText.trim().isEmpty()) {
                    log.warn("OCR produced empty result for file: {}", filePath);
                    throw new VisionApiException("OCR could not extract text from the image. Image quality may be insufficient.");
                }

                log.info("OCR completed successfully. Extracted {} characters", extractedText.length());
                return extractedText;
            }

            throw new VisionApiException("Vision API returned empty response");
        } catch (IOException e) {
            log.error("Error performing OCR: {}", e.getMessage(), e);
            throw new VisionApiException("Failed to perform OCR", e);
        }
    }

    /**
     * Assess the quality of OCR results
     *
     * @param text Extracted text from OCR
     * @return Quality score from 0.0 to 1.0
     */
    public double assessOcrQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        // Simple quality heuristics
        int totalChars = text.length();
        int koreanChars = 0;
        int specialChars = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
                Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO) {
                koreanChars++;
            } else if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                specialChars++;
            }
        }

        // If too many special characters relative to Korean text, quality is low
        double specialCharRatio = (double) specialChars / totalChars;
        double koreanCharRatio = (double) koreanChars / totalChars;

        if (specialCharRatio > 0.3) {
            return 0.5;
        }

        if (koreanCharRatio > 0.3) {
            return 0.9;
        }

        return 0.7;
    }

    /**
     * Fallback for image content detection
     */
    private boolean detectImageContentFallback(String filePath, Exception e) {
        log.error("Vision API circuit breaker activated for image detection: {}", e.getMessage());
        return true; // Assume it needs OCR when API is unavailable
    }

    /**
     * Fallback for OCR processing
     */
    private String performOcrFallback(String filePath, Exception e) {
        log.error("Vision API circuit breaker activated for OCR: {}", e.getMessage());
        throw new VisionApiException("Vision API is currently unavailable. Please try again later.", e);
    }

    /**
     * Custom exception for Vision API errors
     */
    public static class VisionApiException extends RuntimeException {
        public VisionApiException(String message) {
            super(message);
        }

        public VisionApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}