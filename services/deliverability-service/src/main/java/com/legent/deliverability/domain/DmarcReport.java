package com.legent.deliverability.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dmarc_reports")
@Getter
@Setter
public class DmarcReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String reportType; // AGGREGATE, FORENSIC

    @Column(columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "parsed_summary", columnDefinition = "JSONB")
    private String parsedSummary;

    @Column(name = "received_at")
    private java.time.Instant receivedAt;
}
