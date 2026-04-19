package com.legent.deliverability.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reputation_scores")
@Getter
@Setter
public class ReputationScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private double score; // 0-100

    @Column(name = "last_updated")
    private java.time.Instant lastUpdated;
}
