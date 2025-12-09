package com.zipduck.domain.pdf;

import com.zipduck.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for PdfDocument write operations
 * T060: PdfCommandService with file storage and cache key generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PdfCommandService {

    private final PdfRepository pdfRepository;
    private final PdfAnalysisResultRepository pdfAnalysisResultRepository;

    @Value("${app.upload.directory:./uploads/pdfs}")
    private String uploadDirectory;

    /**
     * Save uploaded PDF file
     * FR-016: Store PDF file and create document record
     * T060: File storage implementation
     */
    public PdfDocument saveUploadedFile(User user, MultipartFile file) throws IOException {
        // Create upload directory if not exists
        Path uploadPath = Paths.get(uploadDirectory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(uniqueFilename);

        // Generate cache key (SHA-256 hash of file content) BEFORE saving file
        // This allows us to check for duplicates without wasting disk space
        String cacheKey = generateCacheKey(file);

        // Check if this file has already been uploaded and processed
        // FR-023: Reuse analysis results for duplicate files to save cost and time
        Optional<PdfDocument> existingDocument = pdfRepository.findByCacheKey(cacheKey);

        if (existingDocument.isPresent()) {
            PdfDocument existing = existingDocument.get();

            log.info("Duplicate file detected (cache_key: {}). User {} uploading same file as User {}",
                cacheKey, user.getId(), existing.getUser().getId());

            // Check if same user
            if (existing.getUser().getId().equals(user.getId())) {
                log.info("Same user re-uploading file. Returning existing document ID: {}", existing.getId());
                return existing;
            } else {
                // Different user uploaded same file
                // Create a new record for this user, but link to same cache_key
                // The analysis will be reused automatically via cache_key matching
                log.info("Different user uploading same file. Creating new record but will reuse analysis results.");

                // Save file for this user (they get their own copy)
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                PdfDocument newDocument = PdfDocument.builder()
                        .user(user)
                        .fileName(originalFilename)
                        .filePath(filePath.toString())
                        .fileSize(file.getSize())
                        .contentType(file.getContentType())
                        .status(existing.getStatus()) // Copy status from existing
                        .cacheKey(cacheKey)
                        .build();

                // If existing document already has analysis, copy the status
                if (existing.getStatus() == PdfDocument.ProcessingStatus.COMPLETED) {
                    log.info("Existing document already processed. New document will share analysis results.");
                }

                return pdfRepository.save(newDocument);
            }
        }

        // New file - save to disk
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create PdfDocument entity
        PdfDocument pdfDocument = PdfDocument.builder()
                .user(user)
                .fileName(originalFilename)
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .status(PdfDocument.ProcessingStatus.PENDING)
                .cacheKey(cacheKey)
                .build();

        return pdfRepository.save(pdfDocument);
    }

    /**
     * Generate SHA-256 cache key for file content
     * FR-023, T060: Cache key generation for duplicate detection
     */
    private String generateCacheKey(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to generate cache key", e);
            return UUID.randomUUID().toString(); // Fallback to UUID
        }
    }

    /**
     * Update PDF document status
     */
    public PdfDocument updateStatus(Long id, PdfDocument.ProcessingStatus status) {
        PdfDocument pdfDocument = pdfRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PDF document not found: " + id));
        pdfDocument.updateStatus(status);
        return pdfRepository.save(pdfDocument);
    }

    /**
     * Mark PDF as processing
     */
    public void markAsProcessing(Long id) {
        PdfDocument pdfDocument = pdfRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PDF document not found: " + id));
        pdfDocument.markAsProcessing();
        pdfRepository.save(pdfDocument);
    }

    /**
     * Mark PDF as completed
     */
    public void markAsCompleted(Long id) {
        PdfDocument pdfDocument = pdfRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PDF document not found: " + id));
        pdfDocument.markAsCompleted();
        pdfRepository.save(pdfDocument);
    }

    /**
     * Mark PDF as failed with error message
     */
    public void markAsFailed(Long id, String errorMessage) {
        PdfDocument pdfDocument = pdfRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PDF document not found: " + id));
        pdfDocument.markAsFailed(errorMessage);
        pdfRepository.save(pdfDocument);
    }

    /**
     * Save PDF analysis result
     * FR-017, FR-018: Store AI analysis results
     */
    public PdfAnalysisResult saveAnalysisResult(PdfAnalysisResult analysisResult) {
        return pdfAnalysisResultRepository.save(analysisResult);
    }

    /**
     * Delete PDF document and associated file
     */
    public void delete(Long id) throws IOException {
        PdfDocument pdfDocument = pdfRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PDF document not found: " + id));

        // Delete physical file
        Path filePath = Paths.get(pdfDocument.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // Delete database records (cascade will delete analysis result)
        pdfRepository.delete(pdfDocument);
    }
}
