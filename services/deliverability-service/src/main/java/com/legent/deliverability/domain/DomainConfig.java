package com.legent.deliverability.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "domains")
@Getter
@Setter
public class DomainConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String status; // PENDING, VERIFIED, BLOCKED

    @Column(name = "spf_status")
    private String spfStatus;

    @Column(name = "dkim_status")
    private String dkimStatus;

    @Column(name = "dmarc_status")
    private String dmarcStatus;

    @Column(name = "last_checked")
    private java.time.Instant lastChecked;
}
