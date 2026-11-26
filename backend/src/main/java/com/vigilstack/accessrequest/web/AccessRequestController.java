package com.vigilstack.accessrequest.web;

import com.vigilstack.accessrequest.model.AccessRequest;
import com.vigilstack.accessrequest.model.SystemName;
import com.vigilstack.accessrequest.model.UserAccount;
import com.vigilstack.accessrequest.service.AccessRequestService;
import com.vigilstack.accessrequest.repo.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/requests")
public class AccessRequestController {

    // Small "shapes" for the JSON the API accepts. The annotations are the validation rules.
    public record CreateBody(
            @NotNull(message = "System is required (GITHUB, AWS, SLACK or GOOGLE_WORKSPACE).") SystemName system,
            @NotBlank(message = "Reason is required.") String reason) { }

    public record ApproveBody(
            String managerName) { }

    public record RejectBody(
            String managerName,
            @NotBlank(message = "A reason is required when rejecting.") String reason) { }

    private final AccessRequestService service;
    private final UserAccountRepository users;

    public AccessRequestController(AccessRequestService service, UserAccountRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequest create(@Valid @RequestBody CreateBody body, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        return service.create(user.getFullName(), body.system(), body.reason(), user.getId());
    }

    @GetMapping
    public List<AccessRequest> list(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        return user.getRole() == com.vigilstack.accessrequest.model.UserRole.MANAGER
                ? service.list() : service.listForEmployee(user.getId());
    }

    @PostMapping("/{id}/approve")
    public AccessRequest approve(@PathVariable Long id, @Valid @RequestBody ApproveBody body,
                                 Authentication authentication) {
        return service.approve(id, currentUser(authentication).getFullName());
    }

    @PostMapping("/{id}/reject")
    public AccessRequest reject(@PathVariable Long id, @Valid @RequestBody RejectBody body,
                                Authentication authentication) {
        return service.reject(id, currentUser(authentication).getFullName(), body.reason());
    }

    private UserAccount currentUser(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Account not found. Please sign in again."));
    }
}
