package com.vigilstack.accessrequest.repo;

import com.vigilstack.accessrequest.model.AccessRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findAllByOrderByCreatedAtDesc();   // newest first
    List<AccessRequest> findAllByRequesterIdOrderByCreatedAtDesc(Long requesterId);
}
