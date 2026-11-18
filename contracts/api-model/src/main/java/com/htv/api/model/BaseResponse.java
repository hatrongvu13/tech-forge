package com.htv.api.model;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseResponse<T> {
    private String traceId;
    private String code;
    private String message;
    private T data;
}
