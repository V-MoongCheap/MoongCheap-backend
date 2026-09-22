package com.moongcheap_backend.support.concurrency;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;

/**
 * 동시성 테스트 베이스. {@link AbstractIntegrationTest}의 TestContainers 셋업과 프로파일을 재사용한다.
 * DB 제약 위반이 GlobalExceptionHandler를 통해 처리되어야 하는 경우 MockMvc로 컨트롤러를 호출한다.
 */
public abstract class AbstractConcurrencyTest extends AbstractIntegrationTest {
}
