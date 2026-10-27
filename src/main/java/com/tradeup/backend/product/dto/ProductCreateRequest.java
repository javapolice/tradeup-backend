package com.tradeup.backend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        @Size(max = 255)
        String description,

        @NotNull
        @PositiveOrZero
        Long price
) {
}
