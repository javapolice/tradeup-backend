package com.tradeup.backend.global.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                exception.getMessage()
        );

        problem.setProperty("code", errorCode.name());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                "요청 값이 올바르지 않습니다."
        );

        problem.setProperty("code", errorCode.name());

        List<Map<String, String>> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(
                        Comparator.comparing(FieldError::getField)
                                .thenComparing(
                                        FieldError::getCode,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                )
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "올바르지 않은 값입니다."
                ))
                .toList();

        problem.setProperty("errors", errors);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                "요청 본문이 없거나 JSON 형식 또는 자료형이 올바르지 않습니다."
        );

        problem.setProperty("code", errorCode.name());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                "요청 경로 또는 파라미터의 자료형이 올바르지 않습니다."
        );

        problem.setProperty("code", errorCode.name());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(problem);
    }
}
