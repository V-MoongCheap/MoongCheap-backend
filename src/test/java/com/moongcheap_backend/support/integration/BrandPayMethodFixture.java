package com.moongcheap_backend.support.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * BrandPayMethod는 프로덕션 코드에서 protected 생성자만 노출하므로 native SQL로 삽입한다.
 */
@Component
public class BrandPayMethodFixture {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public Long saveActive(Long memberId) {
        String key = "test-key-" + System.nanoTime();
        return jdbcTemplate.queryForObject("""
                INSERT INTO brand_pay_method
                    (member_id, method_key, provider_code, masked_number, type,
                     is_default, status, created_at, updated_at)
                VALUES
                    (?, ?, 'TOSS', '****-1234', 'CARD',
                     true, 'ACTIVE', NOW(), NOW())
                RETURNING id
                """, Long.class, memberId, key);
    }
}
