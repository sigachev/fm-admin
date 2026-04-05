package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserDetailDto extends AdminUserDto {

    private List<AdminPortfolioSummaryDto> portfolios = new ArrayList<>();
}
