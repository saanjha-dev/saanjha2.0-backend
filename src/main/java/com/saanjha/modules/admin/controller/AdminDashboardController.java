package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminResponseDTOs.DashboardOverviewResponse;
import com.saanjha.modules.admin.service.AdminDashboardService;
import com.saanjha.shared.api.ApiEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "20. Admin - Dashboard")
@PreAuthorize("hasAuthority('admin:moderate')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Dashboard Overview: queue depths, active suspensions/announcements, high-risk users")
    public ResponseEntity<ApiEnvelope<DashboardOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiEnvelope.success(dashboardService.getOverview()));
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Recent hourly snapshots for trend charts")
    public ResponseEntity<ApiEnvelope<?>> snapshots() {
        return ResponseEntity.ok(ApiEnvelope.success(dashboardService.getRecentSnapshots()));
    }
}
