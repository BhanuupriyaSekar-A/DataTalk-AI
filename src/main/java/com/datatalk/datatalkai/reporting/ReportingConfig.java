package com.datatalk.datatalkai.reporting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for reporting components that are plain Java classes (easy to unit test).
 */
@Configuration
public class ReportingConfig {

    @Bean
    public SqlSafetyValidator sqlSafetyValidator(@Value("${app.reporting.max-sql-length:6000}") int maxSqlLength) {
        return new SqlSafetyValidator(maxSqlLength);
    }
}
