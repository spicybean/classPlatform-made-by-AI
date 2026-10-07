---
description: "Spring Boot / JPA / Java 25 환경에 특화된 백엔드 아키텍처 및 보안 코딩 불변식"
---

# Spring Boot 백엔드 아키텍처 및 보안 규칙 (Spring Backend Rule)

이 규칙은 `project_one` 내의 모든 Spring Boot 및 Java 백엔드 소스코드를 작성·수정할 때 전역 규칙(`backend_universal.md`)의 구체적 구현체로 상시 강제 적용됩니다.

---

## 1. 트랜잭션 격리 및 JPA 성능 불변식
- **읽기 전용 기본화**: 모든 Service 클래스는 클래스 레벨에 `@Transactional(readOnly = true)`를 반드시 선언한다.
- **선택적 쓰기 트랜잭션**: 데이터를 등록, 수정, 삭제하는 CUD 메서드에만 명시적으로 `@Transactional`을 부여한다.
- **지연 로딩(Lazy Loading) 강제**: `@ManyToOne`, `@OneToOne`, `@OneToMany` 등 모든 JPA 연관관계 매핑은 반드시 `fetch = FetchType.LAZY`를 명시한다 (기본 EAGER 로딩 방지 및 N+1 쿼리 폭탄 차단).
- **단건 조회 Fail-Fast**: 조회 메서드는 `findById().orElseThrow(() -> new IllegalArgumentException("..."))` 패턴을 사용하여 데이터 부재 시 즉시 명시적 예외를 발생시킨다.

## 2. Spring Security 및 리소스 소유권(BOLA/IDOR) 가드
- **인증 주체 Null 방어**: Controller에서 `@AuthenticationPrincipal CustomUserDetails userDetails`를 수신할 때, 비로그인 상태일 가능성을 전제하고 반드시 `userDetails == null` 가드를 최우선으로 검사한다.
- **수평적 권한 검증**: 파라미터로 전달된 수업/과제/제출물 등의 리소스를 조작하기 전, 해당 엔티티의 소유자 ID(예: `classRoom.getInstructor().getId()`)와 세션 사용자의 ID(`userDetails.getUser().getId()`)가 일치하는지 반드시 검증한다. 불일치 시 즉각적인 인가 예외를 던진다.

## 3. 엔티티 상태 캡슐화 및 도메인 주도 검증
- **Setter 남발 금지**: 엔티티 클래스에 무분별한 `@Setter` 추가를 금지하며, 외부에서 엔티티 필드를 임의로 직접 변경하지 않는다.
- **도메인 메서드 강제**: 상태 변경(수정, 마감, 상태 전이)은 엔티티 내부의 의미 있는 비즈니스 메서드(예: `updateStatus()`, `assertEditable()`)를 통해서만 수행한다.
- **보관(Archive) 가드**: 수정 가능한 상태인지 검증하는 가드 메서드(예: `classRoom.assertEditable()`)를 CUD 로직 진입부에 배치한다.

## 4. DTO 계층 분리 및 Bean Validation
- Controller와 Service 간 데이터 교환 시 JPA Entity를 직접 수신하거나 웹 계층으로 반환하지 않는다.
- 입력값은 전용 Request DTO(예: `CreateAssignmentDto`, `StartAttendanceDto`)로 바인딩하고 `@Valid`, `@NotBlank`, `@Size` 등의 유효성 검증을 필수로 적용한다.
- 출력값은 전용 Response DTO로 변환하여 엔티티의 내부 필드(암호, 내부 연관 엔티티)가 누출되지 않도록 한다.

## 5. 암호학적 난수 생성 규격
- 출석 인증 번호(4자리), 수업 초대 코드(6자리) 등 모든 랜덤 코드는 `java.util.Random`이나 `Math.random()`을 금지하고 반드시 `java.security.SecureRandom`을 사용하여 생성한다.
