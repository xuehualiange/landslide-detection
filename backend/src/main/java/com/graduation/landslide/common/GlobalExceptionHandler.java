package com.graduation.landslide.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<String> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "参数校验失败"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<String> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "参数绑定失败"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<String> handleConstraintViolation(ConstraintViolationException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<String> handleNotReadable(HttpMessageNotReadableException ex) {
        return ApiResponse.fail("请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String msg = "系统异常，请稍后重试";
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                String detail = ex.getClass().getSimpleName();
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    detail += ": " + ex.getMessage();
                }
                if (detail.length() > 240) {
                    detail = detail.substring(0, 240) + "...";
                }
                msg = detail;
                break;
            }
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(500, msg, null));
    }
}
