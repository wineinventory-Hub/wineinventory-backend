package com.wineinventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the security rules: public endpoints, JWT authentication,
 * resource ownership and role based access.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record SignedInUser(long accountId, String token) {}

    private SignedInUser signUpAndSignIn(String role) throws Exception {
        var username = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        var password = "Secret123!";
        var signUp = """
                {"username":"%s","email":"%s@example.com","password":"%s","validationPassword":"%s","role":"%s"}
                """.formatted(username, username, password, password, role);
        mockMvc.perform(post("/api/v1/sign-up").contentType(MediaType.APPLICATION_JSON).content(signUp))
                .andExpect(status().isOk());

        var signIn = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        var response = mockMvc.perform(post("/api/v1/sign-in").contentType(MediaType.APPLICATION_JSON).content(signIn))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new SignedInUser(body.get("accountId").asLong(), body.get("token").asText());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsProblemDetail401() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void wrongCredentialsReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerPassesTheOwnershipCheck() throws Exception {
        var producer = signUpAndSignIn("PRODUCER");
        // A new account has no warehouses yet, so the controller answers 404 instead of 403
        mockMvc.perform(get("/api/v1/accounts/{accountId}/warehouses", producer.accountId())
                        .header("Authorization", "Bearer " + producer.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotReadAnotherAccountsWarehouses() throws Exception {
        var producer = signUpAndSignIn("PRODUCER");
        var distributor = signUpAndSignIn("DISTRIBUTOR");
        mockMvc.perform(get("/api/v1/accounts/{accountId}/warehouses", producer.accountId())
                        .header("Authorization", "Bearer " + distributor.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void distributorCannotCreateCareGuides() throws Exception {
        var distributor = signUpAndSignIn("DISTRIBUTOR");
        var careGuide = """
                {"guideName":"Red wine storage","type":"STORAGE","description":"Keep between 12 and 14 C","imageUrl":null}
                """;
        mockMvc.perform(post("/api/v1/accounts/{accountId}/care-guides", distributor.accountId())
                        .header("Authorization", "Bearer " + distributor.token())
                        .contentType(MediaType.APPLICATION_JSON).content(careGuide))
                .andExpect(status().isForbidden());
    }
}
