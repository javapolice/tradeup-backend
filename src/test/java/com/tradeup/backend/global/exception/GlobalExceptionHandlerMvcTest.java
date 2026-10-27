package com.tradeup.backend.global.exception;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Runs DispatcherServlet, exception resolution and JSON message conversion.
        // Explicit registration isolates this contract from DB and Security filters.
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INVALID_INPUT | 400 | 잘못된 요청입니다.",
            "UNAUTHORIZED | 401 | 인증이 필요하거나 인증 정보가 올바르지 않습니다.",
            "FORBIDDEN | 403 | 접근 권한이 없습니다.",
            "NOT_FOUND | 404 | 요청한 리소스를 찾을 수 없습니다.",
            "CONFLICT | 409 | 요청이 현재 리소스 상태와 충돌합니다.",
            "INTERNAL_ERROR | 500 | 서버 내부 오류가 발생했습니다."
    })
    void 비즈니스_예외는_정확한_HTTP_상태와_ProblemDetail_JSON을_반환한다(
            String code, int expectedStatus, String expectedDetail
    ) throws Exception {
        mockMvc.perform(get("/test/business-errors/{code}", code)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.errors").doesNotHaveJsonPath())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isExactlyInstanceOf(BusinessException.class));
    }

    @Test
    void 정상_응답은_예외_응답으로_변환되지_않는다() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"))
                .andExpect(result -> assertThat(result.getResolvedException()).isNull());
    }

    @Test
    void 처리_대상이_아닌_예외를_비즈니스_예외로_변환하지_않는다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/test/unhandled-error")))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("unhandled test error");
    }

    // A non-static inner controller cannot be picked up by application component scanning.
    @RestController
    class ExceptionTestController {

        @GetMapping("/test/business-errors/{code}")
        public String businessError(@PathVariable String code) {
            throw new BusinessException(ErrorCode.valueOf(code));
        }

        @GetMapping("/test/success")
        public String success() {
            return "ok";
        }

        @GetMapping("/test/unhandled-error")
        public String unhandledError() {
            throw new IllegalArgumentException("unhandled test error");
        }
    }
}
