package com.htv.starter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GrpcInterceptorsTest {

    private static final GetUserPermissionsRequest REQ =
            GetUserPermissionsRequest.newBuilder().setUserId("u").build();

    private ListAppender<ILoggingEvent> appender;
    private Logger accessLogger;

    @BeforeEach
    void attachLog() {
        accessLogger = (Logger) LoggerFactory.getLogger(GrpcAccessLogInterceptor.LOGGER_NAME);
        accessLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void cleanup() {
        accessLogger.detachAppender(appender);
        ContextHolder.clear();
    }

    private static PermissionQueryServiceGrpc.PermissionQueryServiceBlockingStub stub(GrpcFixture f) {
        return PermissionQueryServiceGrpc.newBlockingStub(f.channel);
    }

    private static RequestContext ctx() {
        return RequestContext.builder().traceId("trace-1").userId("user-1").channel("web").build();
    }

    // ---------------------------------------------------------------- ngữ cảnh

    @Test
    void contextTravelsFromClientToServerWhenUserIsTrusted() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of());
             ContextHolder.Scope ignored = ContextHolder.open(ctx())) {
            stub(f).getUserPermissions(REQ);
            assertThat(f.service.seenUserId).isEqualTo("user-1");
            assertThat(f.service.seenTraceId).isEqualTo("trace-1");
            assertThat(f.service.seenChannel).isEqualTo("web");
        }
    }

    @Test
    void userIdIsIgnoredWhenServerDoesNotTrustMetadata() throws Exception {
        try (GrpcFixture f = new GrpcFixture(false, List.of());
             ContextHolder.Scope ignored = ContextHolder.open(ctx())) {
            stub(f).getUserPermissions(REQ);
            assertThat(f.service.seenUserId).isNull();
            assertThat(f.service.seenTraceId).isEqualTo("trace-1");
        }
    }

    @Test
    void traceparentWinsAndUnsafeValuesAreDropped() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            Metadata md = new Metadata();
            md.put(GrpcMetadataKeys.TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            md.put(GrpcMetadataKeys.TRACE_ID, "ignored");
            md.put(GrpcMetadataKeys.USER_ID, "bad value with spaces");
            stub(f).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md)).getUserPermissions(REQ);
            assertThat(f.service.seenTraceId).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(f.service.seenUserId).isNull();

            stub(f).getUserPermissions(REQ);
            assertThat(f.service.seenTraceId).matches("[0-9a-f]{32}");
        }
    }

    @Test
    void contextIsCleanedAfterEachCall() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            try (ContextHolder.Scope ignored = ContextHolder.open(ctx())) {
                stub(f).getUserPermissions(REQ);
            }
            // gọi tiếp không có ngữ cảnh: server không còn thấy userId của lần trước
            stub(f).getUserPermissions(REQ);
            assertThat(f.service.seenUserId).isNull();
            assertThat(ContextHolder.current()).isEmpty();
        }
    }

    // ------------------------------------------------------------------- lỗi

    @Test
    void businessExceptionBecomesStatusWithErrorCodeTrailerAndNoDetail() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "user 42 missing in table iam_user");
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOfSatisfying(
                    StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                        assertThat(e.getStatus().getDescription()).isEqualTo("Resource not found");
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("COMMON-4040");
                    });
        }
    }

    @Test
    void unexpectedExceptionIsInternalWithoutLeakingMessage() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new IllegalStateException("jdbc:postgresql://secret-host password=hunter2");
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOfSatisfying(
                    StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                        assertThat(e.getStatus().getDescription()).isEqualTo("Internal error");
                        assertThat(String.valueOf(e.getMessage())).doesNotContain("hunter2").doesNotContain("secret");
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("COMMON-5000");
                    });
        }
    }

    @Test
    void statusExceptionIsPassedThrough() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw Status.RESOURCE_EXHAUSTED.withDescription("slow down").asRuntimeException();
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOfSatisfying(
                    StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                        assertThat(e.getStatus().getDescription()).isEqualTo("slow down");
                    });
        }
    }

    @Test
    void securityExceptionsAreMappedWithoutCompileDependency() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new org.springframework.security.access.AccessDeniedException("nope");
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOfSatisfying(
                    StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("COMMON-4030");
                    });
            f.service.handler = r -> {
                throw new org.springframework.security.authentication.BadCredentialsException("bad");
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOfSatisfying(
                    StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("COMMON-4010");
                    });
        }
    }

    @Test
    void unexpectedErrorsInvokeTheLoggingCallback() {
        java.util.concurrent.atomic.AtomicBoolean logged = new java.util.concurrent.atomic.AtomicBoolean();
        GrpcErrors.Failure f = GrpcErrors.toFailure(new RuntimeException("x"), t -> logged.set(true));
        assertThat(logged).isTrue();
        assertThat(f.status().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    // ------------------------------------------------------------- phía client

    @Test
    void clientErrorBecomesBusinessExceptionKeepingRemoteCode() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new BusinessException(CommonErrorCode.CONFLICT);
            };
            assertThatThrownBy(() -> GrpcErrors.translate(() -> stub(f).getUserPermissions(REQ)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode().code()).isEqualTo("COMMON-4090");
                        assertThat(e.getErrorCode().httpStatus()).isEqualTo(409);
                        assertThat(e.getErrorCode().grpcStatus()).isEqualTo(6);
                    });
        }
    }

    @Test
    void clientErrorWithoutTrailerFallsBackToStatusMapping() {
        BusinessException e = GrpcErrors.toBusinessException(Status.UNAVAILABLE.asRuntimeException());
        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThat(GrpcErrors.toBusinessException(Status.DEADLINE_EXCEEDED.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.TIMEOUT);
        assertThat(GrpcErrors.toBusinessException(Status.DATA_LOSS.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
        assertThat(GrpcErrors.toBusinessException(Status.INVALID_ARGUMENT.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(GrpcErrors.toBusinessException(Status.NOT_FOUND.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(GrpcErrors.toBusinessException(Status.PERMISSION_DENIED.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(GrpcErrors.toBusinessException(Status.UNAUTHENTICATED.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThat(GrpcErrors.toBusinessException(Status.RESOURCE_EXHAUSTED.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
        assertThat(GrpcErrors.toBusinessException(Status.ALREADY_EXISTS.asRuntimeException()).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void httpStatusMapping() {
        assertThat(GrpcErrors.httpStatusOf(Status.Code.OK)).isEqualTo(200);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.INVALID_ARGUMENT)).isEqualTo(400);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.UNAUTHENTICATED)).isEqualTo(401);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.PERMISSION_DENIED)).isEqualTo(403);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.NOT_FOUND)).isEqualTo(404);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.ABORTED)).isEqualTo(409);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.RESOURCE_EXHAUSTED)).isEqualTo(429);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.UNIMPLEMENTED)).isEqualTo(501);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.UNAVAILABLE)).isEqualTo(503);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.DEADLINE_EXCEEDED)).isEqualTo(504);
        assertThat(GrpcErrors.httpStatusOf(Status.Code.INTERNAL)).isEqualTo(500);
    }

    // --------------------------------------------------------------- access log

    @Test
    void accessLogHasOneLineWithStatusAndNoPayload() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of());
             ContextHolder.Scope ignored = ContextHolder.open(ctx())) {
            stub(f).getUserPermissions(GetUserPermissionsRequest.newBuilder().setUserId("super-secret-id").build());
            assertThat(appender.list).hasSize(1);
            String line = appender.list.get(0).getFormattedMessage();
            assertThat(line).contains("htv.identity.v1.PermissionQueryService/GetUserPermissions -> OK")
                    .contains("userId=user-1").contains("traceId=trace-1");
            assertThat(line).doesNotContain("super-secret-id");
        }
    }

    @Test
    void accessLogRecordsFinalStatusAfterExceptionMapping() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            };
            assertThatThrownBy(() -> stub(f).getUserPermissions(REQ)).isInstanceOf(StatusRuntimeException.class);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("-> PERMISSION_DENIED");
        }
    }

    @Test
    void excludedMethodsAreNotLogged() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of("htv.identity.v1.PermissionQueryService/*"))) {
            stub(f).getUserPermissions(REQ);
            assertThat(appender.list).isEmpty();
        }
    }

    // ---------------------------------------------------------------- deadline

    @Test
    void defaultDeadlineIsAddedOnlyWhenMissing() {
        io.grpc.CallOptions[] seen = new io.grpc.CallOptions[1];
        io.grpc.Channel next = new io.grpc.Channel() {
            @Override
            public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> newCall(io.grpc.MethodDescriptor<ReqT, RespT> m,
                                                                        io.grpc.CallOptions o) {
                seen[0] = o;
                return null;
            }

            @Override
            public String authority() {
                return "x";
            }
        };
        var method = PermissionQueryServiceGrpc.getGetUserPermissionsMethod();

        new DeadlineClientInterceptor(java.time.Duration.ofSeconds(5)).interceptCall(method,
                io.grpc.CallOptions.DEFAULT, next);
        assertThat(seen[0].getDeadline()).isNotNull();

        io.grpc.CallOptions own = io.grpc.CallOptions.DEFAULT.withDeadlineAfter(1, java.util.concurrent.TimeUnit.HOURS);
        new DeadlineClientInterceptor(java.time.Duration.ofSeconds(5)).interceptCall(method, own, next);
        assertThat(seen[0].getDeadline()).isSameAs(own.getDeadline());

        new DeadlineClientInterceptor(java.time.Duration.ZERO).interceptCall(method, io.grpc.CallOptions.DEFAULT, next);
        assertThat(seen[0].getDeadline()).isNull();
        new DeadlineClientInterceptor(null).interceptCall(method, io.grpc.CallOptions.DEFAULT, next);
        assertThat(seen[0].getDeadline()).isNull();
    }
}
