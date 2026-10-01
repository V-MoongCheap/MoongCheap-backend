package com.moongcheap_backend.payments.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 브랜드페이 인증 토큰과 Access Token 만료 시각을 보관한다.
 * 토큰 필드에는 원문이 아닌 암호화된 값만 저장한다.
 */
@Entity
@Getter
@Table(name = "brand_pay_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandPayToken extends BaseTimeEntity {

    @Id
    private Long memberId;

    /**
     * 동시에 같은 토큰을 읽은 요청 중 오래된 저장이 최신 값을 덮어쓰지 못하게 한다.
     * Hibernate는 UPDATE 조건에 기존 version을 포함하고 저장 성공 시 값을 증가시킨다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "access_token_expires_at")
    private LocalDateTime accessTokenExpiresAt;

    /** 최초 브랜드페이 인증을 완료한 회원의 암호화 토큰을 생성한다. */
    public BrandPayToken(Member member, String accessToken, String refreshToken,
        LocalDateTime accessTokenExpiresAt) {
        this.member = member;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    /** 재인증 또는 토큰 재발급 결과로 기존 암호화 토큰과 만료 시각을 교체한다. */
    public void update(String accessToken, String refreshToken,
        LocalDateTime accessTokenExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }
}
