package com.iotmining.services.tms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanyWithUsersResponse {
    private TenantSummaryResponse company;
    private List<TenantSummaryResponse> users;
    private List<CompanyWithUsersResponse> subCompanies;
}
