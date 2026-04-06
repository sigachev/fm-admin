package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a new token/asset.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenRequest {
    private String symbol;
    private String name;
    private Integer rank;
}
