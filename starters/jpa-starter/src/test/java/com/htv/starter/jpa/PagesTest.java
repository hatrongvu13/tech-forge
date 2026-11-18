package com.htv.starter.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.api.model.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagesTest {

    @Test
    void mapsPageMetadataAndContent() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(2, 3), 10);
        PageResponse<String> r = Pages.toResponse(page, i -> "n" + i);
        assertThat(r.items()).containsExactly("n1", "n2", "n3");
        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(3);
        assertThat(r.totalElements()).isEqualTo(10);
        assertThat(r.totalPages()).isEqualTo(4);
        assertThat(r.hasNext()).isTrue();
        assertThat(Pages.toResponse(page).items()).containsExactly(1, 2, 3);
    }
}
