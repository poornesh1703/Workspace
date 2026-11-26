package com.vigilstack.accessrequest.web;

import com.vigilstack.accessrequest.model.UserRole;
import com.vigilstack.accessrequest.repo.UserAccountRepository;
import com.vigilstack.accessrequest.model.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record LoginCredentials(
        @NotBlank(message = "Name is required.") String fullName,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") String email,
        @NotBlank(message = "Password is required.") String password,
        UserRole role) { }

    public record Registration(
        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be 100 characters or fewer.")
        String fullName,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be 254 characters or fewer.")
        String email,
        @NotBlank(message = "Mobile number is required.")
        @Size(max = 24, message = "Enter a valid mobile number.")
        String mobileNumber,
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        String password,
        @NotNull(message = "Choose an account type.") UserRole role,
        String managerInviteCode) { }

    public record ProfileUpdate(
        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be 100 characters or fewer.")
        String fullName,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be 254 characters or fewer.")
        String email,
        @NotBlank(message = "Mobile number is required.")
        @Size(max = 24, message = "Enter a valid mobile number.")
        String mobileNumber,
        @NotBlank(message = "Current password is required.") String currentPassword,
        @Size(max = 350000, message = "Profile photo must be 256 KB or smaller.")
        @Pattern(regexp = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$", message = "Profile photo must be a PNG, JPEG or WebP image.")
        String profilePhoto) { }

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final String managerInviteCode;

    public AuthController(UserAccountRepository users,
                          @Value("${app.auth.manager-invite-code:}") String managerInviteCode,
                          PasswordEncoder encoder) {
        this.users = users;
        this.managerInviteCode = managerInviteCode;
        this.encoder = encoder;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody Registration body, HttpServletRequest request) {
        String fullName = body.fullName().trim();
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        String mobileNumber = normalizeMobileNumber(body.mobileNumber());
        UserRole role = body.role();
        if (role == UserRole.MANAGER && (managerInviteCode.isBlank()
                || !managerInviteCode.equals(body.managerInviteCode()))) {
            throw new IllegalArgumentException("Manager registration requires a valid invitation code.");
        }
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("That email address is already registered.");
        }
        if (users.findByMobileNumber(mobileNumber).isPresent()) {
            throw new IllegalArgumentException("That mobile number is already registered.");
        }
        UserAccount user = users.save(new UserAccount(email, fullName, mobileNumber, encoder.encode(body.password()), role));
        establishSession(user, request);
        return profileResponse(user);
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginCredentials body, HttpServletRequest request) {
        UserRole role = body.role() == null ? UserRole.EMPLOYEE : body.role();
        UserAccount user = users.findByEmailIgnoreCase(body.email().trim())
            .filter(account -> account.getRole() == role)
            .orElseThrow(() -> new IllegalArgumentException("Account not found. Register first."));
        if (!user.getFullName().equalsIgnoreCase(body.fullName().trim())) {
            throw new IllegalArgumentException("Account details do not match.");
        }
        if (!encoder.matches(body.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password.");
        }
        establishSession(user, request);
        return profileResponse(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @PutMapping("/profile/{id}")
    public Map<String, String> updateProfile(@PathVariable Long id, @RequestParam UserRole role,
                                             @Valid @RequestBody ProfileUpdate body,
                                             org.springframework.security.core.Authentication authentication,
                                             HttpServletRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(authentication.getName())
            .filter(account -> account.getId().equals(id) && account.getRole() == role)
            .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (!encoder.matches(body.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        String fullName = body.fullName().trim();
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        String mobileNumber = normalizeMobileNumber(body.mobileNumber());

        users.findByEmailIgnoreCase(email).filter(account -> !account.getId().equals(id)).ifPresent(account -> {
            throw new IllegalArgumentException("That email address is already registered.");
        });
        users.findByMobileNumber(mobileNumber).filter(account -> !account.getId().equals(id)).ifPresent(account -> {
            throw new IllegalArgumentException("That mobile number is already registered.");
        });

        user.updateProfile(fullName, email, mobileNumber, body.profilePhoto());
        users.save(user);
        establishSession(user, request);
        return profileResponse(user);
    }

    private Map<String, String> profileResponse(UserAccount user) {
        Map<String, String> response = new HashMap<>();
        response.put("id", user.getId().toString());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("mobileNumber", user.getMobileNumber());
        response.put("role", user.getRole().name());
        if (user.getProfilePhoto() != null) response.put("profilePhoto", user.getProfilePhoto());
        return response;
    }

    private void establishSession(UserAccount user, HttpServletRequest request) {
        if (request.getSession(false) != null) request.changeSessionId();
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private String normalizeMobileNumber(String value) {
        String mobileNumber = value.trim().replaceAll("[\\s().-]", "");
        if (!mobileNumber.matches("\\+?[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("Enter a valid mobile number with 7 to 15 digits.");
        }
        return mobileNumber;
    }
}
