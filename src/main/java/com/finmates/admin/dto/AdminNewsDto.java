package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminNewsDto {

    private Long id;
    private String title;
    private String summary;
    private String url;
    private String source;
    private boolean isBreaking;
    private List<String> symbols;
    private OffsetDateTime publishedAt;
    private Long createdBy;
    private OffsetDateTime createdAt;
}
