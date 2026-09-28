package com.moongcheap_backend.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "member_social",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_member_social_provider",
                columnNames = {"provider", "provider_id"}
        ))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "refresh_token_enc", columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private SocialCredential(Long memberId, SocialProvider provider, String providerId,
        String refreshTokenEnc) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerId = providerId;
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public void updateRefreshToken(String refreshTokenEnc) {
        this.refreshTokenEnc = refreshTokenEnc;
    }
}
