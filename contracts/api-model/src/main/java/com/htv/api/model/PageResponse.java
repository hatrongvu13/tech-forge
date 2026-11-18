package com.htv.api.model;

import java.util.List;

/**
 * Một trang dữ liệu, đặt trong {@code BaseResponse.data}. Trang đánh số từ 0.
 *
 * <pre>
 * { "items": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
 * </pre>
 *
 * @param items         phần tử của trang (không null)
 * @param page          số trang, từ 0
 * @param size          kích thước trang yêu cầu (lớn hơn 0)
 * @param totalElements tổng số phần tử của mọi trang
 * @param totalPages    tổng số trang
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("totals must be >= 0");
        }
        items = List.copyOf(items == null ? List.of() : items);
    }

    /** Dựng trang và tự tính {@code totalPages}. */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1); // không tràn số
        int totalPages = (int) Math.min(Integer.MAX_VALUE, pages);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    /** Có trang sau không. */
    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
