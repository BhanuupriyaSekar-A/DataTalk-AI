package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.ai.AiException;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.ReportAccessException;
import com.datatalk.datatalkai.reporting.ReportExecutionException;
import com.datatalk.datatalkai.reporting.SqlValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps reporting/AI exceptions to consistent ApiResponse errors. Messages never contain secrets.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {ChatApiController.class, ReportApiController.class, ExportApiController.class,
        SearchApiController.class, CustomerAdminApiController.class, RecordApiController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(ReportAccessException.class)
    public ResponseEntity<ApiResponse<Void>> access(ReportAccessException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(SqlValidationException.class)
    public ResponseEntity<ApiResponse<Void>> validation(SqlValidationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ReportExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> execution(ReportExecutionException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error("EXECUTION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiResponse<Void>> ai(AiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error("AI_ERROR", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> database(org.springframework.dao.DataAccessException e) {
        log.error("Database error in reporting API", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("DATABASE_ERROR", "A database error occurred. Please try again."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", e.getMessage()));
    }
}
