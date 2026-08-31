package com.saanjha.modules.feedback.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "feedback", schema = "fdb")
@Getter @Setter
public class Feedback extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String category;

    @Column
    private Integer rating;

    @Column(length = 2000)
    private String content;

    @Column(name = "page_url", length = 2048)
    private String pageUrl;
}
