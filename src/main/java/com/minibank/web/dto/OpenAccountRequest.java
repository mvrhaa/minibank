package com.minibank.web.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenAccountRequest(@NotBlank String ownerUsername) {
}
