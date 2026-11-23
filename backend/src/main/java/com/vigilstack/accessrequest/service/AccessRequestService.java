package com.vigilstack.accessrequest.service;

import com.vigilstack.accessrequest.model.AccessRequest;
import com.vigilstack.accessrequest.model.SystemName;
import com.vigilstack.accessrequest.repo.AccessRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

    private final AccessRequestRepository repository;

    public AccessRequestService(AccessRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccessRequest create(String employeeName, SystemName system, String reason) {
        return repository.save(new AccessRequest(employeeName.trim(), system, reason.trim()));
    }

    @Transactional(readOnly = true)
    public List<AccessRequest> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AccessRequest approve(Long id, String managerName) {
        AccessRequest request = find(id);
        request.approve(managerName.trim());
        return request;   // JPA saves the change automatically at the end of the transaction
    }

    @Transactional
    public AccessRequest reject(Long id, String managerName, String reason) {
        AccessRequest request = find(id);
        request.reject(managerName.trim(), reason.trim());
        return request;
    }

    private AccessRequest find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Request " + id + " not found."));
    }
}
