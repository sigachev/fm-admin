package com.finmates.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserDto {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String keycloakId;
    @JsonProperty("isActive")
    private boolean enabled;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
