package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.chat.ChatRecord;
import com.datatalk.datatalkai.chat.ChatService;
import com.datatalk.datatalkai.export.ExportFormat;
import com.datatalk.datatalkai.export.ExportJobService;
import com.datatalk.datatalkai.export.ReportExportService;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportExecutionService;
import com.datatalk.datatalkai.reporting.ReportFilters;
import com.datatalk.datatalkai.reporting.SecureQuery;
import com.datatalk.datatalkai.reporting.UserScope;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * PDF (default) and Excel exports of stored chat reports. Small exports are streamed directly;
 * large ones run as background jobs that the UI polls.
 */
@RestController
@RequestMapping("/api")
public class ExportApiController {

    private final AccessPolicyService accessPolicy;
    private final ChatService chatService;
    private final ReportExportService exportService;
    private final ExportJobService jobService;
    private final ReportExecutionService counter;
    private final int asyncThreshold;

    public ExportApiController(AccessPolicyService accessPolicy, ChatService chatService,
                               ReportExportService exportService, ExportJobService jobService,
                               ReportExecutionService counter,
                               @Value("${app.export.async-threshold-rows:2000}") int asyncThreshold) {
        this.accessPolicy = accessPolicy;
        this.chatService = chatService;
        this.exportService = exportService;
        this.jobService = jobService;
        this.counter = counter;
        this.asyncThreshold = asyncThreshold;
    }

    @GetMapping("/chat/{chatId}/export")
    public ResponseEntity<?> export(@PathVariable int chatId,
                                    @RequestParam(defaultValue = "pdf") String format,
                                    @RequestParam(required = false) String dateFrom,
                                    @RequestParam(required = false) String dateTo,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "false") boolean async,
                                    HttpSession session,
                                    HttpServletResponse response) throws IOException {
        UserScope scope = accessPolicy.resolve(session);
        ExportFormat fmt = ExportFormat.from(format);
        ChatRecord record = chatService.findOwned(scope, chatId);
        SecureQuery query = chatService.secureQueryFor(scope, chatId, ReportFilters.of(dateFrom, dateTo, status));
        String title = record.title() == null ? "Report" : record.title();
        String generatedFor = scope.username() + " (" + scope.role() + ")";

        long rows = counter.count(query);
        if (async || rows > asyncThreshold) {
            ExportJobService.Job job = jobService.submit(scope.userId(), chatId, fmt, query, title, generatedFor);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                    Map.of("jobId", job.getId(), "status", job.getStatus().name(), "rows",
                            Math.min(rows, exportService.maxRows())),
                    "Large export started in the background"));
        }

        response.setContentType(fmt.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(ReportExportService.fileName(title, fmt)).build().toString());
        try {
            long written = exportService.write(fmt, query, title, generatedFor, response.getOutputStream());
            jobService.recordDirect(scope.userId(), chatId, fmt, title, written, null);
        } catch (IOException | RuntimeException e) {
            jobService.recordDirect(scope.userId(), chatId, fmt, title, 0, e.getMessage() == null ? "Export failed" : e.getMessage());
            throw e;
        }
        response.flushBuffer();
        return null;
    }

    @GetMapping("/exports/{jobId}")
    public ApiResponse<ExportJobService.Job> status(@PathVariable String jobId, HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        return ApiResponse.success(jobService.get(jobId, scope.userId()));
    }

    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId, HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        ExportJobService.Job job = jobService.get(jobId, scope.userId());
        FileSystemResource file = new FileSystemResource(jobService.fileOf(job));
        ExportFormat fmt = ExportFormat.valueOf(job.getFormat());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fmt.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(job.getFileName()).build().toString())
                .body(file);
    }
}
