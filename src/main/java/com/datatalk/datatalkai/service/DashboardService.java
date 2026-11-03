package com.datatalk.datatalkai.service;

import com.datatalk.datatalkai.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Dashboard Service
 * Provides data for dashboard pages
 */
@Service
public class DashboardService {

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Get dashboard summary for a specific customer
     */
    public Map<String, Object> getCustomerDashboard(Integer customerId) {
        return customerRepository.getDatabaseSummary(customerId);
    }

    /**
     * Get system dashboard summary (for ADMIN users)
     */
    public Map<String, Object> getSystemDashboard() {
        return customerRepository.getSystemSummary();
    }
}
