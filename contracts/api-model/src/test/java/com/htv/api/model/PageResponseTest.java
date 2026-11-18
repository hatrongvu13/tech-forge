package com.htv.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void ofComputesTotalPages() {
        assertThat(PageResponse.of(List.of("a"), 0, 20, 135).totalPages()).isEqualTo(7);
        assertThat(PageResponse.of(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(PageResponse.of(List.of("a"), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThat(PageResponse.of(List.of("a"), 0, 20, 21).totalPages()).isEqualTo(2);
        assertThat(PageResponse.of(List.of(), 0, 1, Long.MAX_VALUE).totalPages()).isEqualTo(Integer.MAX_VALUE);
        assertThat(PageResponse.of(List.of(), 0, 20, Long.MAX_VALUE).totalPages()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void hasNext() {
        assertThat(PageResponse.of(List.of(), 0, 20, 135).hasNext()).isTrue();
        assertThat(PageResponse.of(List.of(), 6, 20, 135).hasNext()).isFalse();
        assertThat(PageResponse.of(List.of(), 0, 20, 0).hasNext()).isFalse();
    }

    @Test
    void itemsAreCopiedAndNeverNull() {
        List<String> src = new ArrayList<>(List.of("a"));
        PageResponse<String> p = PageResponse.of(src, 0, 10, 1);
        src.add("b");
        assertThat(p.items()).containsExactly("a");
        assertThatThrownBy(() -> p.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new PageResponse<String>(null, 0, 1, 0, 0).items()).isEmpty();
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> PageResponse.of(List.of(), -1, 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResponse.of(List.of(), 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 10, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
