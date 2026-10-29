package com.tradeup.backend.product.dto;

import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductUpdateRequestTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @MethodSource("validValues")
    void 필드_존재와_값을_구분하여_변환한다(String json, ProductUpdateRequest expected) {
        assertThat(ProductUpdateRequest.from(mapper.readTree(json))).isEqualTo(expected);
    }

    static Stream<Arguments> validValues() {
        return Stream.of(
                Arguments.of("{\"title\":\"title\"}", new ProductUpdateRequest(true, "title", false, null, false, null)),
                Arguments.of("{\"description\":\"description\"}", new ProductUpdateRequest(false, null, true, "description", false, null)),
                Arguments.of("{\"description\":null}", new ProductUpdateRequest(false, null, true, null, false, null)),
                Arguments.of("{\"description\":\"\"}", new ProductUpdateRequest(false, null, true, "", false, null)),
                Arguments.of("{\"price\":0}", new ProductUpdateRequest(false, null, false, null, true, 0L)),
                Arguments.of("{\"price\":9223372036854775807}", new ProductUpdateRequest(false, null, false, null, true, Long.MAX_VALUE)),
                Arguments.of("{\"title\":\" title \",\"description\":\"  \",\"price\":1000}",
                        new ProductUpdateRequest(true, " title ", true, "  ", true, 1000L)),
                Arguments.of("{\"title\":\"" + "가".repeat(255) + "\",\"description\":\"" + "나".repeat(255) + "\"}",
                        new ProductUpdateRequest(true, "가".repeat(255), true, "나".repeat(255), false, null)));
    }

    @ParameterizedTest
    @MethodSource("com.tradeup.backend.product.support.ProductPatchCases#invalidValues")
    void 잘못된_값은_INVALID_INPUT이다(String json) {
        assertInvalid(() -> ProductUpdateRequest.from(mapper.readTree(json)));
    }

    @Test
    void Java_null도_INVALID_INPUT이다() {
        assertInvalid(() -> ProductUpdateRequest.from(null));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isExactlyInstanceOf(BusinessException.class)
                .hasMessage("상품 수정 요청 값이 올바르지 않습니다.")
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
