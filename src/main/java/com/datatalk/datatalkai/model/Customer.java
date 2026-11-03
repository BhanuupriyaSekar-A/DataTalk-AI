package com.datatalk.datatalkai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer entity representing a business customer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private Integer customerId;
    private String customerName;
    private String email;
    private String phone;
    private String status;  // ACTIVE, INACTIVE, SUSPENDED, ARCHIVED
    private String createdDate;
}
