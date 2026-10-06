# 소스 코드 문제로 인한 테스트 실패 정리

전체 도메인(auth/category/common/demand/member/notification/product) 단위 테스트 실행 결과, 다음 항목은 **테스트 코드가 아닌 소스 코드**에 문제가 있어 실패한 케이스입니다. 수정 없이 정리만 해두었으니 소스 코드를 확인해 주세요.

---

## 1. `SellerRegistrationService.register` — save() 반환값 미사용

### 위치
- `src/main/java/com/moongcheap_backend/auth/application/SellerRegistrationService.java:60-66`

### 현재 소스
```java
Seller seller = Seller.builder()
        .memberId(memberId)
        .businessName(...)
        ...
        .build();
seller.approve();
sellerRepository.save(seller);           // ← 반환값을 버리고 있음

member.becomeSeller();

SessionPrincipal refreshed = principalFactory.build(member);
sessionManager.refreshPrincipal(httpRequest, refreshed);
return seller.getId();                    // ← 로컬 seller의 id를 반환
```

### 문제점
- `sellerRepository.save(seller)` 의 반환값을 무시하고, 빌더로 생성한 로컬 `seller` 인스턴스의 `getId()` 를 그대로 반환합니다.
- 실 운영에서는 JPA `@GeneratedValue(strategy = IDENTITY)` 부수효과로 인해 저장 후 원본 엔티티의 id 필드가 자동 세팅되므로 우연히 동작합니다.
- 하지만 이는 다음과 같은 위험이 있습니다.
  1. **비 IDENTITY 전략**(예: sequence, application-generated, UUID) 사용 시 id가 원본 인스턴스에 세팅되지 않을 수 있음
  2. **단위 테스트가 어려움**: 리포지토리를 Mock 하면 save() 부수효과가 발생하지 않아 id 검증 불가
  3. Spring Data JPA 관용적 패턴에서 벗어남 (`S save(S entity)` 의 반환값을 사용하는 것이 표준)

### 실제 테스트 실패
- **테스트**: `SellerRegistrationServiceSuccessTest.유효한_정보로_판매자_등록을_요청한다()`
- **파일**: `src/test/java/com/moongcheap_backend/auth/unit/application/success/SellerRegistrationServiceSuccessTest.java:71`
- **에러**: `AssertionError` — 기대값 `10L`, 실제값 `null`
- 테스트 코드는 관용적 패턴에 따라 `save()` 가 id가 세팅된 엔티티를 반환하도록 Mock 처리했으나, 서비스가 반환값을 사용하지 않아 id가 null로 반환됨.

### 권장 수정 방향 (소스)
```java
Seller saved = sellerRepository.save(seller);
member.becomeSeller();
SessionPrincipal refreshed = principalFactory.build(member);
sessionManager.refreshPrincipal(httpRequest, refreshed);
return saved.getId();
```
