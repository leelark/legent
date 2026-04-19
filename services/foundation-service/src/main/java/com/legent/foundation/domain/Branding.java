package com.legent.foundation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branding")
@Getter
@Setter
public class Branding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String logoUrl;

    @Column(nullable = false)
    private String primaryColor;

    @Column(nullable = false)
    private String secondaryColor;
}
