# 시스템 아키텍처

MoongCheap 백엔드(Spring Boot, Java 25)의 전체 시스템 구성과 주요 흐름을 정리한다.

## 1. 전체 아키텍처

```mermaid
flowchart TB
    subgraph Client["클라이언트"]
        Web["Web / Mobile"]
    end

    subgraph Spring["Spring Boot Application (Java 25)"]
        direction TB
        subgraph CommonLayer["공통 레이어"]
            Filter["RequestIdFilter<br/>(MDC: requestId)"]
            GEH["GlobalExceptionHandler<br/>(MVC 전용)"]
            Retry["RetryLoggingConfig<br/>(resilience4j)"]
        end

        subgraph Presentation["Presentation (REST Controllers)"]
            AuthCtl["auth"]
            MemberCtl["member"]
            ProductCtl["product / category"]
            DemandCtl["demand"]
            GroupBuyCtl["groupbuy"]
            OrderCtl["order"]
            PayCtl["payments / payout"]
            NotiCtl["notification"]
        end

        subgraph Application["Application (Use Cases)"]
            AuthSvc["AuthLoginService<br/>AuthSignUpService<br/>OAuth2*Handler"]
            PaySvc["PaymentExecutionService<br/>CreatePayMethodService"]
            GroupSvc["GroupBuyService<br/>GroupBuyJudgmentService"]
            OrderSvc["OrderService"]
            DemandSvc["DemandBoardService<br/>(chunk batch)"]
            ProductSvc["ProductCatalog*"]
        end

        subgraph AsyncLayer["Async / Scheduler / Worker"]
            PaymentWorker["PaymentWorkerPool<br/>(1s fixedDelay)"]
            PayRecover["PaymentRecoveryScheduler"]
            GroupSched["GroupBuyJudgmentScheduler"]
            GroupOutboxPub["GroupBuyOutboxPublisher"]
            PayOutboxPub["PaymentOutboxPublisher"]
            OrderConsumer["GroupBuyOrderCreationConsumer<br/>(Streams consumer)"]
            UnlinkWorker["ProviderUnlinkWorker"]
            DemandCancel["DemandBoardCancelScheduler"]
            ProductIdx["ProductIndexInitializer"]
        end

        subgraph Infra["Infrastructure (Adapters)"]
            JPA["JPA Repositories<br/>+ Flyway"]
            TossC["Toss BrandPay<br/>Payment/Auth/Method Client"]
            GoogleC["GoogleOAuth2Client"]
            KakaoC["KakaoOAuth2Client"]
            OSRepo["ProductCatalogSearchRepository<br/>(CircuitBreaker + fallback)"]
            RedisAdapter["Redis Streams /<br/>ZSet / Session Adapter"]
        end
    end

    subgraph Datastores["데이터스토어"]
        PG[("PostgreSQL 16<br/>- 비즈니스 데이터<br/>- Outbox 테이블<br/>- Flyway 스키마")]
        Redis[("Redis<br/>- Session (moongcheap:session)<br/>- Streams (order-creation)<br/>- ZSet (payment:scheduled)<br/>- 로그인 실패 카운터")]
        OS[("OpenSearch<br/>- product catalog index")]
    end

    subgraph External["외부 서비스"]
        Toss["Toss BrandPay<br/>(결제/취소/조회)"]
        Google["Google OAuth2<br/>+ refresh token"]
        Kakao["Kakao OAuth2<br/>+ unlink"]
    end

    Web --> Filter --> Presentation
    Presentation --> Application
    Application --> Infra
    AsyncLayer --> Infra

    JPA <--> PG
    RedisAdapter <--> Redis
    OSRepo <--> OS
    TossC <--> Toss
    GoogleC <--> Google
    KakaoC <--> Kakao

    OSRepo -. "circuit open 시 fallback" .-> JPA
    GEH -. 적용 .- Presentation
```

## 2. 비동기 메시징 흐름 (Outbox + Streams + ZSet)

Outbox는 세 종류의 이벤트를 가진다 (`OutboxEventType`):
`GROUP_BUY_ORDER_CREATION_REQUESTED`, `GROUP_BUY_JUDGMENT_SCHEDULED`, `PAYMENT_SCHEDULE_SYNC`.
공동구매 생성 트랜잭션에서 앞 두 개가 함께 INSERT 되고, 결제 쪽은 판정 성사 이후 별도 트랜잭션에서 세 번째 이벤트가 INSERT 된다.

```mermaid
flowchart TB
    subgraph TxA["TX A: 공동구매 생성 (GroupBuyService.register)"]
        direction LR
        SaveGB["GroupBuy 저장"] --> OutboxA1["outbox:<br/>GROUP_BUY_ORDER_CREATION_REQUESTED"]
        SaveGB --> OutboxA2["outbox:<br/>GROUP_BUY_JUDGMENT_SCHEDULED<br/>(scheduledAt = endAt + delay)"]
    end

    OutboxDB[("PostgreSQL<br/>outbox_event")]
    OutboxA1 --> OutboxDB
    OutboxA2 --> OutboxDB

    GBPub["GroupBuyOutboxPublisher<br/>→ GroupBuyOutboxPublishService<br/>(fixedDelay polling)"]
    OutboxDB -- findPublishableForUpdate --> GBPub
    GBPub -- "ORDER_CREATION_REQUESTED<br/>XADD" --> Stream[("Redis Stream<br/>moongcheap:group-buy:order-creation")]
    GBPub -- "JUDGMENT_SCHEDULED<br/>ZADD(score=판정시각)" --> JudgeZSet[("Redis ZSet<br/>group-buy:judgment:schedule")]

    Stream -- XREADGROUP --> Consumer["GroupBuyOrderCreationConsumer<br/>(group: group-buy-order-creators)"]
    Consumer -- 참여자별 주문 생성 --> OrderDB[("PostgreSQL<br/>orders")]
    Consumer -. ack 실패 시 재전달 .- Stream

    JudgeSched["GroupBuyJudgmentScheduler<br/>(fixedDelay)"] -- findDue --> JudgeZSet
    JudgeSched -- judge() --> JudgeSvc["GroupBuyJudgmentService<br/>(성사/미달 판정, 별도 TX)"]
    JudgeSvc --> OrderDB
    JudgeSched -- "성사 시 호출" --> Reserve["GroupPaymentReservationService<br/>.scheduleForGroup(groupBuyId)"]
    JudgeSched -- remove --> JudgeZSet

    subgraph TxB["TX B: 결제 예약 (PaymentPreparationService.schedule)"]
        Reserve --> Prep["PaymentPreparationService"]
        Prep --> ExecSync["PaymentExecutionService.sync()"]
        ExecSync --> OutboxB["outbox:<br/>PAYMENT_SCHEDULE_SYNC"]
    end
    OutboxB --> OutboxDB

    PayPub["PaymentOutboxPublisher<br/>(fixedDelay polling)"]
    OutboxDB -- "PAYMENT_SCHEDULE_SYNC" --> PayPub
    PayPub -- "ZADD(score=실행시각)" --> PayZSet[("Redis ZSet<br/>payment:execution:scheduled")]

    Worker["PaymentWorkerPool<br/>(Lua: atomic claim + visibility)"] -- ZRANGEBYSCORE --> PayZSet
    Worker -- "HTTP POST<br/>Idempotency-Key" --> Toss["Toss BrandPay"]
    Worker -- 결과 저장 --> OrderDB
    Recovery["PaymentRecoveryScheduler"] -. 실패/타임아웃 복구 .-> Worker
```

핵심:
- **Outbox 생성은 두 지점**뿐이다: ① 공동구매 생성 TX에서 주문요청·판정예약 2건, ② 판정 성사 뒤 결제 예약 TX에서 1건.
- 주문 생성(Order) 자체는 Outbox를 만들지 않는다. 주문은 Stream 메시지를 받은 Consumer가 생성한다.
- 결제는 "판정 → 예약 → outbox → publisher → ZSet → worker" 순서로 흐른다.

## 3. 인증 흐름

```mermaid
flowchart TB
    Browser["Browser"]

    subgraph Local["로컬 로그인"]
        AuthCtl["AuthController"] --> LoginSvc["AuthLoginService"]
        LoginSvc --> MemberDB[("PostgreSQL<br/>member")]
        LoginSvc -. 실패 카운트 .- RedisFail[("Redis<br/>login-fail counter")]
    end

    subgraph OAuth["OAuth2 (Google / Kakao)"]
        SpringSec["Spring Security<br/>OAuth2 Entry"]
        UserSvc["CustomOAuth2UserService"]
        Success["OAuth2LoginSuccessHandler"]
        SessionMgr["AuthSessionManager"]
    end

    Browser --> AuthCtl
    Browser --> SpringSec --> UserSvc --> Success --> SessionMgr
    SessionMgr --> RedisSession[("Redis<br/>moongcheap:session")]
    Success -- refresh token 저장 --> MemberDB
    UserSvc -. userinfo .- GoogleAPI["Google"]
    UserSvc -. userinfo .- KakaoAPI["Kakao"]

    Unlink["ProviderUnlinkWorker<br/>(@Scheduled)"] -- unlink API --> KakaoAPI
    Unlink -- token revoke --> GoogleAPI
```

## 핵심 포인트

- **헥사고날 4-레이어**: `presentation → application → domain → infrastructure`를 도메인별로 반복.
- **데이터 저장소 역할 분리**: PostgreSQL(사실 소스), Redis(세션/큐/카운터), OpenSearch(검색, 서킷 열리면 Postgres로 폴백).
- **이벤트는 Outbox 경유**: 비즈니스 트랜잭션에서 outbox 테이블 insert만 하고, 별도 퍼블리셔가 Redis Streams/ZSet로 발행하여 at-least-once를 보장.
- **GlobalExceptionHandler는 MVC 요청 전용**: `@Scheduled`·`@Async`·Streams 컨슈머·`PaymentWorkerPool`·`ProviderUnlinkWorker`는 자체 try/catch로 로깅 책임.
- **외부 호출은 resilience4j로 감쌈**: Toss는 Idempotency-Key, OpenSearch는 CircuitBreaker + Postgres fallback.
