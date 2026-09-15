package com.moongcheap_backend.product.infrastructure.productAwardEvaluation;

import com.moongcheap_backend.product.domain.productAwardEvaluation.ProductAwardEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAwardEvaluationRepository
    extends JpaRepository<ProductAwardEvaluation, Long> {

}
