package com.moongcheap_backend.common.schema;

/**
 * 내부 API(<code>/internal</code>) 계약 스키마 버전.
 *
 * <p>외부(AI 서비스 등)와 주고받는 payload의 스키마 버전을 한 곳에서 관리합니다.
 *
 * <p><b>사용 범위</b>
 * <ul>
 *   <li>Response(우리가 보내는 것): 상수를 직접 세팅해 응답에 포함</li>
 *   <li>Request(외부가 보내는 것): <b>강제 검증하지 않음</b>.
 *       문서·참조용 상수로만 두고, 실제 request DTO에는 별도 제약을 걸지 않음.
 *       (외부 발신자가 다른 버전으로 보내도 무리 없이 처리하기 위함)</li>
 * </ul>
 *
 * <p><b>버전 정책 (SemVer 응용)</b>: {@code <name>.v<MAJOR>.<MINOR>}
 * <ul>
 *   <li>MAJOR: 필드 제거·이름 변경·타입 변경 등 breaking change</li>
 *   <li>MINOR: 필드 추가 등 backward-compatible change</li>
 *   <li>v0.x: 개발 중, 호환성 보장 없음</li>
 *   <li>v1.0: 최초 안정 릴리즈</li>
 * </ul>
 *
 * <p>DTO 필드 변경 시 반드시 함께 bump하고 CHANGELOG에 기록할 것.
 */
public final class InternalSchemaVersions {

    /** POST /internal/formation-plans — 수요 클러스터링 결과 request (외부 → 우리, 참조용) */
    public static final String FORMATION_PLAN = "formation-plan.v0.1";

    /** POST /internal/substitute-offer-plans — 대체 상품 제안 계획 request (외부 → 우리, 참조용) */
    public static final String SUBSTITUTE_OFFER_PLAN = "substitute-offer-plan.v0.1";

    /** POST /internal/result — AI 낙찰 결과 반영 request (외부 → 우리, 참조용) */
    public static final String AWARDING_RESULT = "awarding-result.v0.1";

    /** GET /internal/pending — AI 판정 대기 보드 조회 response (우리 → 외부, 응답 세팅용) */
    public static final String AWARDING_PENDING = "awarding-pending.v0.1";

    private InternalSchemaVersions() {
    }
}
