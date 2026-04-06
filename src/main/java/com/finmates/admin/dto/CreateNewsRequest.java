package com.finmates.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateNewsRequest {

    @NotBlank
    private String title;

    private String summary;
    private String content;
    private String url;
    private String source = "FinMates";
    private boolean isBreaking;
    private List<String> symbols;
}
