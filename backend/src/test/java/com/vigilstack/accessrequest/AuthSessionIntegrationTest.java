package com.vigilstack.accessrequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-session-test",
        "app.auth.manager-invite-code=session-test-invite"
})
@AutoConfigureMockMvc
class AuthSessionIntegrationTest {
    @Autowired MockMvc mvc;
        @Autowired ObjectMapper mapper;

    @Test
    void employeeRegistrationCreatesSession_andLogoutRevokesIt() throws Exception {
        String registration = """
                {"fullName":"Session Employee","email":"session.employee@example.com","mobileNumber":"+14155550321","password":"employee-pass-25","role":"EMPLOYEE"}
                """;
        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertNotNull(session, "Successful registration must create an authenticated server session");

        mvc.perform(get("/api/requests").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/requests").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void managerNeedsInvitation_andCanOnlyUseManagerRoleAfterRegistration() throws Exception {
        String noInvite = """
                {"fullName":"No Invite","email":"no.invite@example.com","mobileNumber":"+14155550322","password":"manager-pass-25","role":"MANAGER"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(noInvite))
                .andExpect(status().isBadRequest());

        String withInvite = """
                {"fullName":"Invited Manager","email":"invited.manager@example.com","mobileNumber":"+14155550323","password":"manager-pass-25","role":"MANAGER","managerInviteCode":"session-test-invite"}
                """;
        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(withInvite))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertNotNull(session);
        String requestBody = "{\"system\":\"GITHUB\",\"reason\":\"Manager test request\"}";
        String requestResponse = mvc.perform(post("/api/requests").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = mapper.readTree(requestResponse).get("id").asLong();
        mvc.perform(post("/api/requests/" + requestId + "/approve").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].actor").value("Invited Manager"));
    }
}
