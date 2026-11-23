package com.vigilstack.accessrequest.web;

import com.vigilstack.accessrequest.model.AccessRequest;
import com.vigilstack.accessrequest.model.SystemName;
import com.vigilstack.accessrequest.service.AccessRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
public class AccessRequestController {

    // Small "shapes" for the JSON the API accepts. The annotations are the validation rules.
    public record CreateBody(
            @NotBlank(message = "Employee name is required.") String employeeName,
            @NotNull(message = "System is required (GITHUB, AWS, SLACK or GOOGLE_WORKSPACE).") SystemName system,
            @NotBlank(message = "Reason is required.") String reason) { }

    public record ApproveBody(
            @NotBlank(message = "Manager name is required.") String managerName) { }

    public record RejectBody(
            @NotBlank(message = "Manager name is required.") String managerName,
            @NotBlank(message = "A reason is required when rejecting.") String reason) { }

    private final AccessRequestService service;

    public AccessRequestController(AccessRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequest create(@Valid @RequestBody CreateBody body) {
        return service.create(body.employeeName(), body.system(), body.reason());
    }

    @GetMapping
    public List<AccessRequest> list() {
        return service.list();
    }

    @PostMapping("/{id}/approve")
    public AccessRequest approve(@PathVariable Long id, @Valid @RequestBody ApproveBody body) {
        return service.approve(id, body.managerName());
    }

    @PostMapping("/{id}/reject")
    public AccessRequest reject(@PathVariable Long id, @Valid @RequestBody RejectBody body) {
        return service.reject(id, body.managerName(), body.reason());
    }
}
