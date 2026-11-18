package com.htv.starter.feign;

import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ErrorCode;
import com.htv.shared.common.RemoteErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lỗi HTTP từ service khác thành {@link BusinessException}. Đọc {@code code}/{@code message} trong
 * {@code BaseResponse} của bên kia để mã lỗi đi xuyên service (ví dụ {@code IDENTITY-4041}).
 * Bên kia không trả {@code BaseResponse} (API bên ngoài): dùng mã chung theo HTTP status.
 *
 * <p>Chỉ nhận {@code code} đúng dạng {@code CHỮ-SỐ} và message ngắn; thân lỗi chỉ đọc tối đa
 * {@code maxBodyBytes}. Nội dung thân lỗi không bao giờ được trả nguyên cho client của mình.
 */
public class BusinessErrorDecoder implements ErrorDecoder {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}-\\d{1,6}$");
    private static final int MAX_MESSAGE = 200;

    private final JsonMapper json = JsonMapper.builder().build();
    private final int maxBodyBytes;

    public BusinessErrorDecoder(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        CommonErrorCode fallback = CommonErrorCode.forHttpStatus(status);
        ErrorCode code = readRemoteCode(response, status, fallback.grpcStatus());
        return new BusinessException(code != null ? code : fallback,
                "Downstream " + methodKey + " returned HTTP " + status);
    }

    private ErrorCode readRemoteCode(Response response, int status, int grpcStatus) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            byte[] bytes = in.readNBytes(maxBodyBytes);
            JsonNode node = json.readTree(bytes);
            if (node == null || !node.isObject()) {
                return null;
            }
            String code = text(node, "code");
            if (code == null || !CODE.matcher(code).matches()) {
                return null;
            }
            String message = text(node, "message");
            if (message == null || message.isBlank()) {
                message = CommonErrorCode.forHttpStatus(status).message();
            }
            if (message.length() > MAX_MESSAGE) {
                message = message.substring(0, MAX_MESSAGE);
            }
            return new RemoteErrorCode(code, message, status, grpcStatus);
        } catch (IOException | RuntimeException e) {
            return null; // thân không phải JSON hợp lệ: dùng mã chung
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }
}
