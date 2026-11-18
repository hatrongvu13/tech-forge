package com.htv.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Khởi động identity-service như một ứng dụng web servlet đầy đủ (security-starter, method security, gRPC,
 * Redis, PostgreSQL). Chưa cấu hình JWT nên mọi đường dẫn đều bị từ chối: đó là hành vi an toàn mặc định.
 */
@SpringBootTest(properties = {"spring.datasource.password=unused", "htv.grpc.server.port=0"})
@AutoConfigureMockMvc
class WebContextIT extends ContainersSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void contextStartsAndDeniesEverythingUntilJwtIsConfigured() throws Exception {
        mvc.perform(get("/any/path")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-4010"));
    }
}
