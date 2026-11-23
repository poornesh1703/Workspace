package com.vigilstack.accessrequest.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_system")
    private SystemName system;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private Instant createdAt;

    // The audit trail. Saved together with the request (cascade) and always loaded with it (eager).
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id")
    @OrderBy("timestamp ASC, id ASC")
    private List<HistoryEntry> history = new ArrayList<>();

    protected AccessRequest() { }   // needed by JPA

    public AccessRequest(String employeeName, SystemName system, String reason) {
        this.employeeName = employeeName;
        this.system = system;
        this.reason = reason;
        this.status = RequestStatus.PENDING;
        this.createdAt = Instant.now();
        this.history.add(new HistoryEntry(HistoryAction.CREATED, employeeName, reason));
    }

    /** Business rule: only a pending request can be decided, and it can be decided only once. */
    public void approve(String manager) {
        requirePending();
        this.status = RequestStatus.APPROVED;
        this.history.add(new HistoryEntry(HistoryAction.APPROVED, manager, null));
    }

    public void reject(String manager, String reason) {
        requirePending();
        this.status = RequestStatus.REJECTED;
        this.history.add(new HistoryEntry(HistoryAction.REJECTED, manager, reason));
    }

    private void requirePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is already " + status + " and cannot be changed.");
        }
    }

    public Long getId() { return id; }
    public String getEmployeeName() { return employeeName; }
    public SystemName getSystem() { return system; }
    public String getReason() { return reason; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<HistoryEntry> getHistory() { return history; }
}
