package com.tradeup.backend.product.dto;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import tools.jackson.databind.JsonNode;

import java.util.Set;

public record ProductUpdateRequest(
        boolean titlePresent,
        String title,
        boolean descriptionPresent,
        String description,
        boolean pricePresent,
        Long price
) {

    public static ProductUpdateRequest from(JsonNode json) {

        if (json == null || !json.isObject()) {
            throw invalidInput();
        }

        Set<String> allowedFields = Set.of(
                "title",
                "description",
                "price"
        );

        var fieldNames = json.propertyNames();

        for (String fieldName : fieldNames) {
            if (!allowedFields.contains(fieldName)) {
                throw invalidInput();
            }
        }

        boolean titlePresent = json.has("title");
        boolean descriptionPresent = json.has("description");
        boolean pricePresent = json.has("price");

        if (!titlePresent && !descriptionPresent && !pricePresent) {
            throw invalidInput();
        }

        String title = null;
        String description = null;
        Long price = null;

        if (titlePresent) {
            JsonNode node = json.get("title");

            if (!node.isString()
                    || node.asString().isBlank()
                    || node.asString().length() > 255) {
                throw invalidInput();
            }

            title = node.asString();
        }

        if (descriptionPresent) {
            JsonNode node = json.get("description");

            if (!node.isNull()) {
                if (!node.isString() || node.asString().length() > 255) {
                    throw invalidInput();
                }

                description = node.asString();
            }
        }

        if (pricePresent) {
            JsonNode node = json.get("price");

            if (!node.isIntegralNumber()
                    || !node.canConvertToLong()
                    || node.longValue() < 0) {
                throw invalidInput();
            }

            price = node.longValue();
        }

        return new ProductUpdateRequest(
                titlePresent,
                title,
                descriptionPresent,
                description,
                pricePresent,
                price
        );
    }

    private static BusinessException invalidInput() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 수정 요청 값이 올바르지 않습니다."
        );
    }
}
