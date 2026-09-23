package com.moongcheap_backend.payout.infrastructure;

import com.moongcheap_backend.payout.domain.SellerKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerKeyRepository extends JpaRepository<SellerKey, Long> {

}
