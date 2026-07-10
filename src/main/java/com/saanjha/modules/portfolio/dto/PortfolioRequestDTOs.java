package com.saanjha.modules.portfolio.dto;

import jakarta.validation.constraints.NotNull;

public class PortfolioRequestDTOs {

    public record UpdateVisibilityRequest(
            @NotNull(message = "visibility is required") String visibility
    ) {}
}
