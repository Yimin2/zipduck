package com.zipduck.api.controller;

import com.zipduck.api.dto.response.ApiResponse;
import com.zipduck.api.dto.response.PdfAnalysisResponse;
import com.zipduck.api.dto.response.PdfStatusResponse;
import com.zipduck.api.dto.response.PdfUploadResponse;
import com.zipduck.api.exception.BusinessException;
import com.zipduck.application.async.PdfAnalysisTask;
import com.zipduck.domain.pdf.PdfAnalysisResult;
import com.zipduck.domain.pdf.PdfCommandService;
import com.zipduck.domain.pdf.PdfDocument;
import com.zipduck.domain.pdf.PdfQueryService;
import com.zipduck.domain.user.User;
import com.zipduck.domain.user.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Controller for PDF upload and analysis
 * T073: PdfController with OpenAPI annotations
 * FR-016, FR-022, FR-036, FR-037, FR-038
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF", description = "PDF upload and analysis APIs")
public class PdfController {

    private final PdfCommandService pdfCommandService;
    private final PdfQueryService pdfQueryService;
    private final UserQueryService userQueryService;
    private final PdfAnalysisTask pdfAnalysisTask;

    // T074: File format and size validation
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("application/pdf", "image/jpeg", "image/jpg", "image/png");

    /**
     * Upload PDF file for analysis
     * FR-016: PDF upload endpoint
     * T074: File format validation and size limits
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload PDF for analysis", description = "Upload subscription PDF (max 10MB, PDF/JPEG/PNG)")
    public ResponseEntity<ApiResponse<PdfUploadResponse>> uploadPdf(
            @Parameter(description = "User ID", required = true) @RequestParam Long userId,
            @Parameter(description = "PDF file", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file) {


        log.info("PDF upload request from user: {}, file: {}", userId, file.getOriginalFilename());

        // T074: Validate file size
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "업로드된 파일이 비어있습니다");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", String.format("파일 크기가 너무 큽니다 (최대 %dMB)", MAX_FILE_SIZE / (1024 * 1024)));
        }

        // T074: Validate file format
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("INVALID_FILE_FORMAT", "지원하지 않는 파일 형식입니다 (PDF, JPEG, PNG만 지원)");
        }

        try {
            // Get user
            User user = userQueryService.getById(userId);

            // Save file
            PdfDocument pdfDocument = pdfCommandService.saveUploadedFile(user, file);

            // Start async analysis
            pdfAnalysisTask.analyzePdfAsync(pdfDocument.getId(), user.getProfile());

            // Return upload response
            PdfUploadResponse response = PdfUploadResponse.from(pdfDocument);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));

        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", e.getMessage(), e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다");
        }
    }

    /**
     * Get PDF processing status
     * FR-022: Check PDF processing status
     */
    @GetMapping("/{pdfId}/status")
    @Operation(summary = "Get PDF processing status", description = "Check the current processing status of uploaded PDF")
    public ResponseEntity<ApiResponse<PdfStatusResponse>> getPdfStatus(@PathVariable Long pdfId) {

        log.debug("Get PDF status request for ID: {}", pdfId);

        PdfDocument pdfDocument = pdfQueryService.getById(pdfId);
        PdfStatusResponse response = PdfStatusResponse.from(pdfDocument);

        // T075: Add OCR quality notification in response
        if (pdfDocument.getStatus() == PdfDocument.ProcessingStatus.COMPLETED) {
            // Check if analysis result has OCR warnings
            if (pdfQueryService.hasAnalysisResult(pdfId)) {
                PdfAnalysisResult analysisResult = pdfQueryService.getAnalysisResultByPdfId(pdfId);
                if (analysisResult.getOcrWarning() != null) {
                    log.info("OCR warning for PDF {}: {}", pdfId, analysisResult.getOcrWarning());
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get PDF analysis results
     * FR-022: Retrieve detailed analysis results
     */
    @GetMapping("/{pdfId}/analysis")
    @Operation(summary = "Get PDF analysis results", description = "Retrieve detailed analysis results including match score")
    public ResponseEntity<ApiResponse<PdfAnalysisResponse>> getAnalysisResults(@PathVariable Long pdfId) {

        log.debug("Get PDF analysis request for ID: {}", pdfId);

        PdfDocument pdfDocument = pdfQueryService.getById(pdfId);

        if (pdfDocument.getStatus() != PdfDocument.ProcessingStatus.COMPLETED) {
            throw new BusinessException("ANALYSIS_NOT_READY", "PDF 분석이 아직 완료되지 않았습니다 (현재 상태: " + pdfDocument.getStatus()
                    .getKoreanName() + ")");
        }

        PdfAnalysisResult analysisResult = pdfQueryService.getAnalysisResultByPdfId(pdfId);
        PdfAnalysisResponse response = PdfAnalysisResponse.from(analysisResult);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}