package com.moongcheap_backend.support.concurrency;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;

/**
 * 동시성 테스트 베이스. {@link AbstractIntegrationTest}의 TestContainers 셋업과 프로파일을 재사용한다.
 * MockMvc는 사용하지 않고 서비스/리포지토리 빈을 직접 호출한다.
 */
public abstract class AbstractConcurrencyTest extends AbstractIntegrationTest {
}
