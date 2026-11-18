package com.htv.starter.jpa;

import com.htv.api.model.PageResponse;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Đổi {@link Page} của Spring Data thành {@link PageResponse}, kèm đổi entity sang DTO. */
public final class Pages {

    private Pages() {
    }

    public static <E, D> PageResponse<D> toResponse(Page<E> page, Function<? super E, ? extends D> mapper) {
        return new PageResponse<>(page.getContent().stream().<D>map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> toResponse(Page<T> page) {
        return toResponse(page, Function.identity());
    }
}
