# Swagger 사용 가이드

## 접속

서버 실행 후 브라우저에서 접속합니다.

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI YAML**: `http://localhost:8080/v3/api-docs.yaml`

YAML 파일을 다운로드하면 Postman, Insomnia 등에 import하여 사용할 수 있습니다.

---

## 인증 (SID 쿠키)

이 프로젝트는 세션 쿠키 기반 인증을 사용합니다.

Swagger UI 우상단 **Authorize** 버튼 → `sessionCookie (apiKey)` → SID 값 입력 후 인증하면
이후 요청에 `Cookie: SID=<값>` 이 자동으로 포함됩니다.

> 소셜 로그인은 브라우저 리다이렉트 방식이라 Swagger에서 직접 실행 불가합니다.
> 브라우저 주소창에 `/oauth2/authorization/kakao` 또는 `/oauth2/authorization/google`을 직접 입력해 로그인 후 SID 쿠키를
> 복사해 사용하세요.

---

## API에 Swagger 애노테이션 붙이기

### 컨트롤러 클래스

```java

@Tag(name = "Auth", description = "회원가입, 로그인, 로그아웃")
@RestController
public class AuthController {

}
```

### 엔드포인트 메서드

```java

@Operation(summary = "로그아웃", description = "FN-B24-02. 현재 세션을 무효화한다.")
@PostMapping("/logout")
public ResponseEntity<Void> logout(HttpServletRequest request) {
}
```

- `summary`: Swagger UI 목록에 표시되는 한 줄 설명
- `description`: 상세 설명. 기능정의서 번호(FN-XXX)를 함께 명시합니다.
  -- description 앞에는 기능 ID를 붙여주세요(없는 경우 기능 ID 없음으로)

### SessionPrincipal 파라미터

`SessionPrincipal`은 세션에서 자동 주입되는 파라미터입니다.
`OpenApiConfig`에서 전역으로 숨김 처리되어 있으므로 별도 처리 없이 사용합니다.

```java
// Swagger 파라미터에 노출되지 않음 — 그대로 사용
public ResponseEntity<Void> complete(SessionPrincipal principal, ...) {
}
```

`SessionPrincipal`이 파라미터에 있으면 해당 엔드포인트에 자동으로 `sessionCookie` 인증 마크가 붙습니다.

---

## 글로벌 예외 처리 추가하기

비즈니스 예외는 두 곳을 함께 수정합니다.

### 1. `ErrorCode` 열거형에 코드 추가

`src/main/java/com/moongcheap_backend/common/exception/ErrorCode.java`

```java
// 도메인별로 묶어서 추가
DEMAND_ALREADY_EXISTS(HttpStatus.CONFLICT, "DEMAND_001","이미 진행 중인 수요 요청이 있습니다."),
```

코드 네이밍 규칙: `{도메인}_{3자리 숫자}` (예: `AUTH_001`, `SHIP_002`, `DEMAND_001`)

### 2. 예외 던지기

서비스 레이어에서 `BusinessException`으로 던집니다.

```java
throw new BusinessException(ErrorCode.DEMAND_ALREADY_EXISTS);

// 메시지를 오버라이드하고 싶을 때
throw new

BusinessException(ErrorCode.DEMAND_ALREADY_EXISTS, "커스텀 메시지");
```

### 3. DB 유니크 제약 위반 처리

`DataIntegrityViolationException`은 `GlobalExceptionHandler`의 `CONSTRAINT_ERROR_MAP`에 제약 이름을 등록하면
됩니다.

`src/main/java/com/moongcheap_backend/common/exception/GlobalExceptionHandler.java`

```java
private static final Map<String, ErrorCode> CONSTRAINT_ERROR_MAP = Map.of(
    "uq_shipping_address_default", ErrorCode.SHIPPING_ADDRESS_DEFAULT_CONFLICT,
    "uq_demand_member_catalog_active", ErrorCode.DEMAND_ALREADY_EXISTS
    // 새 제약 추가 시 여기에 등록
);
```

제약 이름은 DDL의 `CONSTRAINT` 이름과 일치해야 합니다.

---

## 에러 응답 문서 관리

Swagger에는 공통 에러(400/401/403/500)를 달지 않습니다.
**엔드포인트별 비즈니스 에러는 `docs/api-error-responses.md`에만 기록합니다.**

### 작성 형식

```markdown
#### `POST /api/auth/social-signup/complete` — 소셜 가입 완료

| HTTP | code     | message                        |
|------|----------|--------------------------------|
| 400  | AUTH_013 | 이미 소셜 가입이 완료되었습니다.   |
| 403  | AUTH_016 | 소셜 가입 완료가 필요합니다.       |
```

### 작성 규칙

- 새 엔드포인트를 추가할 때마다 해당 파일에 섹션을 추가합니다.
- 공통 에러(COMMON_400, COMMON_401, COMMON_403, COMMON_500, COMMON_409)는 이미 상단에 명시되어 있으므로 중복 기재하지 않습니다.
- `ErrorCode`에 코드를 추가했다면 반드시 이 파일에도 반영합니다.
