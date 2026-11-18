package com.htv.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.htv.shared.common.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc;

    GlobalExceptionHandlerTest() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mvc = MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestContextFilter(java.util.List.of()))
                .build();
    }

    @Test
    void businessExceptionUsesErrorCodeStatusAndSafeMessage() throws Exception {
        mvc.perform(get("/t/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-4040"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                // detail ("order 42 missing") chỉ vào log, không trả cho client
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("42"))));
    }

    @Test
    void business5xxMapsToServiceUnavailable() throws Exception {
        mvc.perform(get("/t/business5xx"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON-5030"));
    }

    @Test
    void unexpectedExceptionDoesNotLeakDetails() throws Exception {
        String body = mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON-5000"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("hunter2").doesNotContain("secret-host").doesNotContain("IllegalState");
    }

    @Test
    void securityExceptionsAreRecognisedWithoutCompileDependency() throws Exception {
        mvc.perform(get("/t/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-4030"));
        mvc.perform(get("/t/unauth")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-4010"));
    }

    @Test
    void downstreamConnectionFailureIs503WithoutDetails() throws Exception {
        String body = mvc.perform(get("/t/downstream-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON-5030"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-host");
    }

    @Test
    void validationErrorsListFields() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("must not be blank"));
    }

    @Test
    void malformedJsonIs400WithoutParserDetails() throws Exception {
        String body = mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-4000"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Unexpected character").doesNotContain("JsonParse");
    }

    @Test
    void wrongMethodIs405AndWrongMediaTypeIs415() throws Exception {
        mvc.perform(post("/t/ctx")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON-4050"));
        mvc.perform(post("/t/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON-4150"));
    }

    @Test
    void statusToCodeMapping() {
        assertThat(GlobalExceptionHandler.codeFor(400)).isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(GlobalExceptionHandler.codeFor(401)).isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThat(GlobalExceptionHandler.codeFor(403)).isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(GlobalExceptionHandler.codeFor(404)).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(GlobalExceptionHandler.codeFor(409)).isEqualTo(CommonErrorCode.CONFLICT);
        assertThat(GlobalExceptionHandler.codeFor(429)).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
        assertThat(GlobalExceptionHandler.codeFor(503)).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThat(GlobalExceptionHandler.codeFor(504)).isEqualTo(CommonErrorCode.TIMEOUT);
        assertThat(GlobalExceptionHandler.codeFor(500)).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
        assertThat(GlobalExceptionHandler.codeFor(502)).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
        assertThat(GlobalExceptionHandler.codeFor(406)).isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
}
