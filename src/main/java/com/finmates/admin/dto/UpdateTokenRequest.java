package com.finmates.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating a token's metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTokenRequest {
    private String name;
    private Integer rank;
    private String logoUrl;
    private String description;
    private String website;
    private String twitter;
    private String whitepaper;
}
