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
    private static final String PROBLEM_JSON = "content['application/problem+json']";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreServedWithGeneralInfo() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Task Management API"))
                .andExpect(jsonPath("$.info.version").value("2.0.0"));
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
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['401']").doesNotExist())
                // 6 task operations + 4 auth operations
                .andExpect(jsonPath("$.paths.*.*.responses['500']", hasSize(10)))
                .andExpect(jsonPath("$.paths.*.*.responses['405']").doesNotExist());
    }

    @Test
    void taskOperationsRequireABearerTokenAndAuthOperationsDoNot() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                // Every task operation documents the 401 it returns without a valid token
                .andExpect(jsonPath("$.paths['/tasks'].*.responses['401']", hasSize(2)))
                .andExpect(jsonPath("$.paths['/tasks/{id}'].*.responses['401']", hasSize(4)))
                // An empty security list overrides the global requirement: no token needed
                .andExpect(jsonPath("$.paths['/auth/register'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/auth/login'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/auth/logout'].post.security", hasSize(0)));
    }

    @Test
    void errorResponsesUseTheProblemDetailSchema() throws Exception {
        String problemSchema = "#/components/schemas/ProblemDetail";

        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/tasks/{id}'].get.responses['404']." + PROBLEM_JSON + ".schema['$ref']")
                        .value(problemSchema))
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['400']." + PROBLEM_JSON + ".schema['$ref']")
                        .value(problemSchema))
                .andExpect(jsonPath("$.paths['/tasks'].post.responses['500']." + PROBLEM_JSON + ".schema['$ref']")
                        .value(problemSchema));
    }

    @Test
    void problemDetailSchemaMatchesTheJsonTheApiReturns() throws Exception {
        String properties = "$.components.schemas.ProblemDetail.properties";

        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(properties + ".type").exists())
                .andExpect(jsonPath(properties + ".title").exists())
                .andExpect(jsonPath(properties + ".status").exists())
                .andExpect(jsonPath(properties + ".detail").exists())
                .andExpect(jsonPath(properties + ".instance").exists())
                .andExpect(jsonPath(properties + ".timestamp").exists())
                .andExpect(jsonPath(properties + ".errors").exists())
                // Spring writes extensions at the top level, not inside a nested "properties" object
                .andExpect(jsonPath(properties + ".properties").doesNotExist());
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
