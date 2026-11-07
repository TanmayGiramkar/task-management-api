package com.example.taskapi.config;

import com.example.taskapi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the generated OpenAPI document and Swagger UI.
 * Loads the full application, so it runs against PostgreSQL in Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationIT {

    private static final String API_DOCS = "/v3/api-docs";
    private static final String JSON = "content['application/json']";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreServedWithGeneralInfo() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Task Management API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    void apiDocsListAllTaskOperations() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].delete").exists());
    }

    @Test
    void eachOperationListsOnlyTheResponsesItCanReturn() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['404']").doesNotExist())
                .andExpect(jsonPath("$.paths['/tasks'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/tasks'].get.responses['404']").doesNotExist())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/tasks/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths.*.*.responses['500']", hasSize(6)))
                .andExpect(jsonPath("$.paths.*.*.responses['405']").doesNotExist());
    }

    @Test
    void errorResponsesUseTheSharedErrorSchemas() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/tasks/{id}'].get.responses['404']." + JSON + ".schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['400']." + JSON + ".schema.oneOf[*]['$ref']",
                        containsInAnyOrder(
                                "#/components/schemas/ValidationErrorResponse",
                                "#/components/schemas/ErrorResponse")))
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['500']." + JSON + ".schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.timestamp").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationErrorResponse.properties.errors").exists());
    }

    @Test
    void queryParametersDocumentTheirRules() throws Exception {
        String parameters = "$.paths['/tasks'].get.parameters";

        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(parameters + "[?(@.name == 'size')].schema.type", contains("integer")))
                .andExpect(jsonPath(parameters + "[?(@.name == 'size')].schema.maximum", contains(50)))
                .andExpect(jsonPath(parameters + "[?(@.name == 'page')].schema.type", contains("integer")))
                .andExpect(jsonPath(parameters + "[?(@.name == 'sortBy')].schema.enum[*]",
                        containsInAnyOrder("id", "title", "status", "createdAt", "updatedAt")))
                .andExpect(jsonPath(parameters + "[?(@.name == 'direction')].schema.enum[*]",
                        containsInAnyOrder("asc", "desc")));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
