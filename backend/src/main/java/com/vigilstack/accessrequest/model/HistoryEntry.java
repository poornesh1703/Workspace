package com.vigilstack.accessrequest.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One line in a request's audit trail: who did what, and when. */
@Entity
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private HistoryAction action;

    private String actor;

    @Column(length = 1000)
    private String comment;   // the rejection reason, or the original reason on creation

    private Instant timestamp;

    protected HistoryEntry() { }   // needed by JPA

    public HistoryEntry(HistoryAction action, String actor, String comment) {
        this.action = action;
        this.actor = actor;
        this.comment = comment;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public HistoryAction getAction() { return action; }
    public String getActor() { return actor; }
    public String getComment() { return comment; }
    public Instant getTimestamp() { return timestamp; }
}
