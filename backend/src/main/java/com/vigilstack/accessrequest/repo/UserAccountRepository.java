package com.vigilstack.accessrequest.repo;

import com.vigilstack.accessrequest.model.UserAccount;
import com.vigilstack.accessrequest.model.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCaseAndRole(String username, UserRole role);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByMobileNumber(String mobileNumber);
    Optional<UserAccount> findByIdAndRole(Long id, UserRole role);
}
