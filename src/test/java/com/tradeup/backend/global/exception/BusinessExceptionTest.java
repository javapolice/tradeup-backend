package com.tradeup.backend.global.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void 예외는_오류_코드의_참조와_메시지를_보존한다(ErrorCode errorCode) {
        BusinessException exception = new BusinessException(errorCode);

        assertThat(exception.getErrorCode()).isSameAs(errorCode);
        assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void 사용자_지정_메시지는_기본_메시지_대신_보존된다(ErrorCode errorCode) {
        String message = "사용자 지정 상세 메시지";
        assertThat(message).isNotEqualTo(errorCode.getMessage());

        BusinessException exception = new BusinessException(errorCode, message);

        assertThat(exception.getErrorCode()).isSameAs(errorCode);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
}
