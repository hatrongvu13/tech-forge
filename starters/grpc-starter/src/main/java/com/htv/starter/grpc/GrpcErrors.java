package com.htv.starter.grpc;

import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ErrorCode;
import com.htv.shared.common.RemoteErrorCode;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.function.Supplier;

/** Dịch lỗi hai chiều giữa {@link BusinessException} và {@link Status} của gRPC. */
public final class GrpcErrors {

    private GrpcErrors() {
    }

    /** Status và trailer để đóng lời gọi. */
    public record Failure(Status status, Metadata trailers) {
    }

    /**
     * Phía server: đổi một exception thành status trả cho client. Thông điệp luôn lấy từ {@link ErrorCode}
     * hoặc cố định "Internal error", không lộ message của exception lạ.
     *
     * @param unexpected được gọi (nếu không null) cho lỗi không lường trước, để ghi log
     */
    public static Failure toFailure(Throwable t, java.util.function.Consumer<Throwable> unexpected) {
        if (t instanceof BusinessException be) {
            ErrorCode code = be.getErrorCode();
            Metadata trailers = new Metadata();
            trailers.put(GrpcMetadataKeys.ERROR_CODE, code.code());
            return new Failure(Status.fromCodeValue(code.grpcStatus()).withDescription(code.message()), trailers);
        }
        if (t instanceof StatusRuntimeException sre) {
            Metadata trailers = sre.getTrailers() == null ? new Metadata() : sre.getTrailers();
            return new Failure(sre.getStatus(), trailers);
        }
        if (t instanceof StatusException se) {
            Metadata trailers = se.getTrailers() == null ? new Metadata() : se.getTrailers();
            return new Failure(se.getStatus(), trailers);
        }
        // Không phụ thuộc Spring Security lúc biên dịch: nhận diện theo tên lớp.
        if (inHierarchy(t, "org.springframework.security.access.AccessDeniedException")) {
            return codeFailure(CommonErrorCode.FORBIDDEN);
        }
        if (inHierarchy(t, "org.springframework.security.core.AuthenticationException")) {
            return codeFailure(CommonErrorCode.UNAUTHORIZED);
        }
        if (unexpected != null) {
            unexpected.accept(t);
        }
        return codeFailure(CommonErrorCode.INTERNAL_ERROR);
    }

    /** Phía client: đổi lỗi gRPC nhận được về {@link BusinessException} mang mã lỗi của service kia. */
    public static BusinessException toBusinessException(StatusRuntimeException e) {
        Metadata trailers = e.getTrailers();
        String code = trailers == null ? null : trailers.get(GrpcMetadataKeys.ERROR_CODE);
        Status status = e.getStatus();
        ErrorCode errorCode = code == null
                ? fromStatus(status)
                : new RemoteErrorCode(code, safeMessage(status), httpStatusOf(status.getCode()),
                status.getCode().value());
        return new BusinessException(errorCode, status.getDescription(), e);
    }

    /** Chạy một lời gọi gRPC chặn; {@link StatusRuntimeException} thành {@link BusinessException}. */
    public static <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw toBusinessException(e);
        }
    }

    static int httpStatusOf(Status.Code code) {
        return switch (code) {
            case OK -> 200;
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE -> 400;
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS, ABORTED -> 409;
            case RESOURCE_EXHAUSTED -> 429;
            case UNIMPLEMENTED -> 501;
            case UNAVAILABLE -> 503;
            case DEADLINE_EXCEEDED -> 504;
            default -> 500;
        };
    }

    private static ErrorCode fromStatus(Status status) {
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> CommonErrorCode.INVALID_REQUEST;
            case UNAUTHENTICATED -> CommonErrorCode.UNAUTHORIZED;
            case PERMISSION_DENIED -> CommonErrorCode.FORBIDDEN;
            case NOT_FOUND -> CommonErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> CommonErrorCode.CONFLICT;
            case RESOURCE_EXHAUSTED -> CommonErrorCode.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> CommonErrorCode.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> CommonErrorCode.TIMEOUT;
            default -> CommonErrorCode.INTERNAL_ERROR;
        };
    }

    private static Failure codeFailure(ErrorCode code) {
        Metadata trailers = new Metadata();
        trailers.put(GrpcMetadataKeys.ERROR_CODE, code.code());
        return new Failure(Status.fromCodeValue(code.grpcStatus()).withDescription(code.message()), trailers);
    }

    private static String safeMessage(Status status) {
        return status.getDescription() == null ? status.getCode().name() : status.getDescription();
    }

    private static boolean inHierarchy(Throwable t, String className) {
        for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
