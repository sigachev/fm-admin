package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating a token's metadata (name, rank).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTokenRequest {
    private String name;
    private Integer rank;
}
