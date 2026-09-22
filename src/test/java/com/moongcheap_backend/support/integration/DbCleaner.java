package com.moongcheap_backend.support.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbCleaner {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Transactional
    public void clearAll() {
        em.createNativeQuery("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE")
            .executeUpdate();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private static final String[] TABLES = {
        "notification_opt_out",
        "notification_event",
        "notification",
        "member_social",
        "member_local",
        "shipping_address",
        "reject_history",
        "product_award_evaluation",
        "payments_webhook",
        "payments",
        "payout",
        "orders",
        "group_buy_likes",
        "group_buy",
        "product",
        "demand",
        "demand_board",
        "product_catalog",
        "category",
        "seller_payout_account",
        "seller_key",
        "seller",
        "brand_pay_token",
        "brand_pay_method",
        "customer_key",
        "member"
    };
}
