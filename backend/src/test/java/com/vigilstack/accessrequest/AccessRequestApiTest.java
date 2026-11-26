package com.vigilstack.accessrequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import com.vigilstack.accessrequest.model.UserAccount;
import com.vigilstack.accessrequest.model.UserRole;
import com.vigilstack.accessrequest.repo.UserAccountRepository;
import com.vigilstack.accessrequest.service.AccessRequestService;
import com.vigilstack.accessrequest.model.SystemName;

/** These tests start the whole app and call the real URLs, like the frontend would. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:accessdb-test", "app.auth.manager-invite-code=test-manager-invite"})
@AutoConfigureMockMvc
@WithMockUser(username = "test.manager@example.com", roles = "MANAGER")
class AccessRequestApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
        @Autowired UserAccountRepository users;
        @Autowired AccessRequestService requestService;

        @BeforeEach
        void addAuthenticatedManagerAccount() {
                users.deleteAll();
                users.save(new UserAccount("test.manager@example.com", "Test Manager", "+14155550000", "not-used-by-mock-auth", UserRole.MANAGER));
        }

    /** Helper: creates a request and returns its id. */
    private long createRequest() throws Exception {
        String body = """
                {"employeeName": "Asha", "system": "GITHUB", "reason": "Need to review pull requests"}
                """;
        String response = mvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    @Test
    void createRequest_startsAsPending_withCreatedHistory() throws Exception {
        String body = """
                {"employeeName": "Asha", "system": "AWS", "reason": "Deploy the website"}
                """;
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.history[0].action").value("CREATED"))
                .andExpect(jsonPath("$.history[0].actor").value("Test Manager"));
    }

    @Test
    void createRequest_withoutReason_returns400() throws Exception {
        String body = """
                {"employeeName": "Asha", "system": "SLACK", "reason": ""}
                """;
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_changesStatus_andRecordsManagerInHistory() throws Exception {
        long id = createRequest();
        mvc.perform(post("/api/requests/" + id + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerName\": \"Ravi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history[1].action").value("APPROVED"))
                .andExpect(jsonPath("$.history[1].actor").value("Test Manager"));
    }

    @Test
    void reject_withoutReason_returns400_andRequestStaysPending() throws Exception {
        long id = createRequest();
        mvc.perform(post("/api/requests/" + id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerName\": \"Ravi\", \"reason\": \"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].status").value("PENDING"));
    }

    @Test
    void reject_withReason_savesReasonInHistory() throws Exception {
        long id = createRequest();
        mvc.perform(post("/api/requests/" + id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerName\": \"Ravi\", \"reason\": \"Not needed for your role\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.history[1].comment").value("Not needed for your role"));
    }

    @Test
    void decidingTwice_returns409() throws Exception {
        long id = createRequest();
        String approve = "{\"managerName\": \"Ravi\"}";
        mvc.perform(post("/api/requests/" + id + "/approve")
                .contentType(MediaType.APPLICATION_JSON).content(approve)).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/approve")
                .contentType(MediaType.APPLICATION_JSON).content(approve)).andExpect(status().isConflict());
    }

    @Test
    void approveUnknownRequest_returns404() throws Exception {
        mvc.perform(post("/api/requests/999999/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerName\": \"Ravi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerManager_requiresAndStoresContactDetails_withoutReturningPassword() throws Exception {
        String registration = """
                {"fullName":"Nora Manager","email":"nora.manager@example.com","mobileNumber":"+1 (415) 555-0134","password":"strong-pass-25","role":"MANAGER","managerInviteCode":"test-manager-invite"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Nora Manager"))
                .andExpect(jsonPath("$.email").value("nora.manager@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("+14155550134"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nora Manager\",\"email\":\"nora.manager@example.com\",\"password\":\"strong-pass-25\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Wrong Name\",\"email\":\"nora.manager@example.com\",\"password\":\"strong-pass-25\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Another Manager\",\"email\":\"NORA.MANAGER@example.com\",\"mobileNumber\":\"+14155550135\",\"password\":\"strong-pass-25\",\"role\":\"MANAGER\",\"managerInviteCode\":\"test-manager-invite\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_requiresEmailAndMobileNumber() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Missing Contact\",\"password\":\"strong-pass-25\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

        @Test
        void managerRegistration_requiresInviteCode() throws Exception {
                mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"fullName\":\"Uninvited Manager\",\"email\":\"uninvited@example.com\",\"mobileNumber\":\"+14155550193\",\"password\":\"manager-pass-25\",\"role\":\"MANAGER\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @org.springframework.security.test.context.support.WithAnonymousUser
        void requestApi_rejectsAnonymousAccess() throws Exception {
                mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
        }

            @Test
            @org.springframework.security.test.context.support.WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
            void employeeCanOnlyReadOwnRequests_andCannotApprove() throws Exception {
                UserAccount employee = users.save(new UserAccount("employee@example.com", "Employee Owner", "+14155550201", "unused", UserRole.EMPLOYEE));
                UserAccount manager = users.findByEmailIgnoreCase("test.manager@example.com").orElseThrow();
                requestService.create(manager.getFullName(), SystemName.AWS, "Manager-owned request", manager.getId());

                String requestBody = "{\"employeeName\":\"Impersonated Name\",\"system\":\"GITHUB\",\"reason\":\"My access request\"}";
                String response = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.employeeName").value("Employee Owner"))
                        .andReturn().getResponse().getContentAsString();
                long id = mapper.readTree(response).get("id").asLong();

                mvc.perform(get("/api/requests"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].id").value(id))
                        .andExpect(jsonPath("$[1]").doesNotExist());

                mvc.perform(post("/api/requests/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"managerName\":\"Employee Owner\"}"))
                        .andExpect(status().isForbidden());
            }

    @Test
        @org.springframework.security.test.context.support.WithMockUser(username = "profile.owner@example.com", roles = "EMPLOYEE")
    void updateProfile_requiresCurrentPassword_andSavesDetailsAndPhoto() throws Exception {
        String registration = """
                {"fullName":"Profile Owner","email":"profile.owner@example.com","mobileNumber":"+14155550191","password":"profile-pass-25","role":"EMPLOYEE"}
                """;
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(registered).get("id").asLong();
        String photo = "data:image/png;base64,aGVsbG8=";
        String profile = """
                {"fullName":"Updated Profile Owner","email":"updated.owner@example.com","mobileNumber":"+14155550192","currentPassword":"profile-pass-25","profilePhoto":"%s"}
                """.formatted(photo);

        mvc.perform(put("/api/auth/profile/" + id).queryParam("role", "EMPLOYEE")
                        .contentType(MediaType.APPLICATION_JSON).content(profile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Profile Owner"))
                .andExpect(jsonPath("$.email").value("updated.owner@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("+14155550192"))
                .andExpect(jsonPath("$.profilePhoto").value(photo))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(put("/api/auth/profile/" + id).queryParam("role", "EMPLOYEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Another Name\",\"email\":\"updated.owner@example.com\",\"mobileNumber\":\"+14155550192\",\"currentPassword\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Profile Owner\",\"email\":\"updated.owner@example.com\",\"password\":\"profile-pass-25\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhoto").value(photo));
    }

        @Test
        @org.springframework.security.test.context.support.WithMockUser(username = "profile.owner@example.com", roles = "EMPLOYEE")
        void employeeProfile_cannotBeReadOrChangedAsAnotherAccount() throws Exception {
                users.save(new UserAccount("profile.owner@example.com", "Profile Owner", "+14155550198",
                        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password"), UserRole.EMPLOYEE));
                UserAccount other = users.save(new UserAccount("other@example.com", "Other Person", "+14155550199", "unused", UserRole.EMPLOYEE));
                mvc.perform(put("/api/auth/profile/" + other.getId()).queryParam("role", "EMPLOYEE")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"fullName\":\"Changed\",\"email\":\"other@example.com\",\"mobileNumber\":\"+14155550199\",\"currentPassword\":\"password\"}"))
                                .andExpect(status().isBadRequest());
        }
}
