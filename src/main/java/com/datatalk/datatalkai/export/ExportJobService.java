package com.datatalk.datatalkai.export;

import com.datatalk.datatalkai.reporting.ReportAccessException;
import com.datatalk.datatalkai.reporting.SecureQuery;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background export jobs for large reports. Files are written to app.export.directory and removed after one hour.
 * Job information is persisted in app.export_job (ExportJobRepository); active jobs are also cached in memory.
 * After a restart, a completed job can still be downloaded while its file exists.
 */
@Slf4j
@Service
public class ExportJobService {

    public enum Status { RUNNING, COMPLETED, FAILED, EXPIRED }

    public static final class Job {
        private final String id;
        private final int userId;
        private final ExportFormat format;
        private final String fileName;
        private final Instant createdAt = Instant.now();
        private volatile Status status = Status.RUNNING;
        private volatile long rows;
        private volatile String error;
        private volatile Path file;

        Job(String id, int userId, ExportFormat format, String fileName) {
            this.id = id;
            this.userId = userId;
            this.format = format;
            this.fileName = fileName;
        }

        public String getId() { return id; }
        public Status getStatus() { return status; }
        public long getRows() { return rows; }
        public String getError() { return error; }
        public String getFileName() { return fileName; }
        public String getFormat() { return format.name(); }
        public Instant getCreatedAt() { return createdAt; }
        int userId() { return userId; }
        Path file() { return file; }
        ExportFormat format() { return format; }
    }

    private static final Duration RETENTION = Duration.ofHours(1);

    private final ReportExportService exportService;
    private final ExportJobRepository repository;
    private final Path directory;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public ExportJobService(ReportExportService exportService, ExportJobRepository repository,
                            @Value("${app.export.directory}") String directory) {
        this.exportService = exportService;
        this.repository = repository;
        this.directory = Path.of(directory);
    }

    /** Records a direct (streamed) export so all exports appear in app.export_job. */
    public void recordDirect(int userId, Integer chatId, ExportFormat format, String title, long rows, String error) {
        String id = UUID.randomUUID().toString();
        repository.insert(id, userId, chatId, title, format, Status.RUNNING.name(), "DIRECT");
        repository.complete(id, error == null ? Status.COMPLETED.name() : Status.FAILED.name(), rows, null, error);
    }

    public Job submit(int userId, Integer chatId, ExportFormat format, SecureQuery query, String title, String generatedFor) {
        cleanup();
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, userId, format, ReportExportService.fileName(title, format));
        jobs.put(id, job);
        repository.insert(id, userId, chatId, title, format, Status.RUNNING.name(), "BACKGROUND");
        executor.submit(() -> {
            try {
                Files.createDirectories(directory);
                Path file = directory.resolve(id + "." + format.extension());
                try (OutputStream out = Files.newOutputStream(file)) {
                    job.rows = exportService.write(format, query, title, generatedFor, out);
                }
                job.file = file;
                job.status = Status.COMPLETED;
                repository.complete(id, Status.COMPLETED.name(), job.rows, file.toAbsolutePath().toString(), null);
            } catch (Exception e) {
                log.warn("Export job {} failed: {}", id, e.getMessage());
                job.error = e.getMessage() == null ? "Export failed" : e.getMessage();
                job.status = Status.FAILED;
                repository.complete(id, Status.FAILED.name(), job.rows, null, job.error);
            }
        });
        return job;
    }

    /** Returns the job only if it belongs to the user (memory first, then app.export_job). */
    public Job get(String jobId, int userId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            job = repository.findForUser(jobId, userId).map(row -> fromRow(row, userId)).orElse(null);
        }
        if (job == null || job.userId() != userId) {
            throw new ReportAccessException(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "Export not found or expired");
        }
        return job;
    }

    private static Job fromRow(Map<String, Object> row, int userId) {
        ExportFormat format = ExportFormat.valueOf((String) row.get("export_format"));
        Job job = new Job((String) row.get("job_id"), userId, format,
                ReportExportService.fileName((String) row.get("report_name"), format));
        job.status = Status.valueOf((String) row.get("job_status"));
        job.rows = row.get("row_count") == null ? 0 : ((Number) row.get("row_count")).longValue();
        job.error = (String) row.get("error_message");
        String location = (String) row.get("file_location");
        job.file = location == null ? null : Path.of(location);
        return job;
    }

    public Path fileOf(Job job) {
        if (job.getStatus() != Status.COMPLETED || job.file() == null || !Files.exists(job.file())) {
            throw new ReportAccessException(HttpStatus.CONFLICT, "EXPORT_NOT_READY", "Export is not ready");
        }
        return job.file();
    }

    void cleanup() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.values().removeIf(j -> {
            if (j.getCreatedAt().isBefore(cutoff)) {
                try {
                    if (j.file() != null) Files.deleteIfExists(j.file());
                } catch (IOException ignored) {
                    // best effort
                }
                repository.markExpired(j.getId());
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
