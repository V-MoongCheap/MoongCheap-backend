package com.moongcheap_backend.common.config;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.function.Predicate;
import org.opensearch.client.opensearch._types.OpenSearchException;

public class OpenSearchFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        // 1. 아예 도달하지 못한 경우
        if (t instanceof ConnectException
            || t instanceof SocketTimeoutException
            || t instanceof NoRouteToHostException
            || t instanceof UnknownHostException) {
            return true;
        }

        // 2. 도달했지만 클러스터가 처리를 거부한 경우
        if (t instanceof OpenSearchException ose) {
            int status = ose.status();
            return status == 429    // Too Many Requests (rejected execution)
                || status == 503    // Service Unavailable
                || status == 502
                || status == 504;
        }

        // 나머지(400 매핑 오류, 404 인덱스 없음 등)는 실패로 세지 않음
        return false;
    }
}
