package com.htv.starter.web;

import com.htv.api.model.BaseResponse;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ErrorCode;
import com.htv.starter.core.LogMasker;
import jakarta.validation.ConstraintViolationException;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Dịch mọi lỗi thành {@link BaseResponse}. Thông điệp trả cho client luôn lấy từ {@link ErrorCode}
 * (an toàn), không lấy từ message của exception. Chi tiết và stack trace chỉ vào log.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Một lỗi của trường dữ liệu đầu vào.
     */
    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.httpStatus() >= 500) {
            log.error("Business error {}: {}", code.code(), LogMasker.mask(ex.getMessage()), ex);
        } else {
            log.warn("Business error {}: {}", code.code(), LogMasker.mask(ex.getMessage()));
        }
        return respond(code.httpStatus(), ApiResponses.error(code));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<List<FieldViolation>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(400, ApiResponses.error(CommonErrorCode.INVALID_REQUEST, violations));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception ex) {
        // Không phụ thuộc Spring Security lúc biên dịch: nhận diện theo tên lớp trong cây kế thừa.
        if (isInHierarchy(ex, "org.springframework.security.access.AccessDeniedException")) {
            return respond(403, ApiResponses.error(CommonErrorCode.FORBIDDEN));
        }
        if (isInHierarchy(ex, "org.springframework.security.core.AuthenticationException")) {
            return respond(401, ApiResponses.error(CommonErrorCode.UNAUTHORIZED));
        }
        // Feign không kết nối được / hết thời gian (I/O): service phía sau không sẵn sàng.
        if (isInHierarchy(ex, "feign.RetryableException")) {
            log.warn("Downstream call failed: {}", ex.getClass().getSimpleName());
            return respond(503, ApiResponses.error(CommonErrorCode.SERVICE_UNAVAILABLE));
        }
        log.error("Unexpected error", ex);
        return respond(500, ApiResponses.error(CommonErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new FieldViolation(e instanceof FieldError f ? f.getField() : e.getObjectName(),
                        e.getDefaultMessage()))
                .toList();
        return body(400, ApiResponses.error(CommonErrorCode.INVALID_REQUEST, violations), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(r.getMethodParameter().getParameterName(),
                                e.getDefaultMessage())))
                .toList();
        return body(400, ApiResponses.error(CommonErrorCode.INVALID_REQUEST, violations), headers);
    }

    /**
     * Mọi exception chuẩn của Spring MVC (405, 415, JSON hỏng, thiếu tham số...) đi qua đây.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        int status = statusCode.value();
        if (status >= 500) {
            log.error("Framework error {}", status, ex);
        } else {
            log.warn("Request rejected {}: {}", status, ex.getClass().getSimpleName());
        }
        return body(status, ApiResponses.error(codeFor(status)), headers);
    }

    static ErrorCode codeFor(int status) {
        return switch (status) {
            case 400 -> CommonErrorCode.INVALID_REQUEST;
            case 401 -> CommonErrorCode.UNAUTHORIZED;
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> CommonErrorCode.CONFLICT;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> CommonErrorCode.TIMEOUT;
            default -> status >= 500 ? CommonErrorCode.INTERNAL_ERROR : CommonErrorCode.INVALID_REQUEST;
        };
    }

    private static boolean isInHierarchy(Throwable ex, String className) {
        for (Class<?> c = ex.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static <T> ResponseEntity<BaseResponse<T>> respond(int status, BaseResponse<T> body) {
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }

    private static ResponseEntity<Object> body(int status, BaseResponse<?> body, HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
