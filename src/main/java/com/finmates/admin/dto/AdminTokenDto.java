package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for token/asset information returned by admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTokenDto {
    private Long id;
    private String symbol;
    private String name;
    private Boolean isActive;  // Use wrapper Boolean to handle nulls properly
    private Integer rank;
    private String logoUrl;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
