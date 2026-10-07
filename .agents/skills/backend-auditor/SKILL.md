---
name: backend-auditor
description: 백엔드 코드의 보안 취약점(BOLA/IDOR, Broken Auth, Mass Assignment), JPA/ORM 성능 누수(N+1, EAGER 로딩), 트랜잭션 정합성을 적대적 관점에서 감사하고 공격 시나리오와 패치를 도출하는 전문 감사 스킬. 백엔드 코드 리뷰, 보안 감사, 아키텍처 점검 요청 시 사용.
---

# 백엔드 적대적 감사관 스킬 (Backend Auditor Skill)

이 스킬은 백엔드 소스코드 및 API 변경 사항에 대해 **"공격자의 관점(Adversarial Mindset)"**으로 보안 취약점, ORM 성능 병목, 트랜잭션 정합성 결함을 정밀 검증하고 실증적인 패치를 제시합니다.

---

## 🎯 4대 핵심 감사 체크리스트

### 1. BOLA / IDOR 및 인가 우회 감사 (OWASP API #1)
- [ ] 경로 변수(`/{id}`)나 파라미터로 전달된 리소스에 대해, 세션 주체(`userId`)의 소유권 또는 역할 인가 검증이 누락되었는가?
- [ ] 다른 사용자의 리소스 ID를 임의로 대입했을 때 수정/삭제/조회가 허용되는 허점이 존재하는가?
- [ ] `@AuthenticationPrincipal` 또는 세션 객체가 `null`일 때 NPE 크래시나 인증 우회가 발생하는가?

### 2. JPA / ORM 성능 및 N+1 쿼리 누수 감사
- [ ] 연관관계 매핑에서 `FetchType.LAZY` 대신 기본 `EAGER` 로딩이 방치되어 불필요한 전체 조인이 발생하는가?
- [ ] 컬렉션을 순회하며 지연 로딩 프록시 객체를 조회하여 N+1 쿼리 폭탄이 유발되는가? (Fetch Join 또는 BatchSize 필요 여부 점검)
- [ ] 변경 감지(Dirty Checking)가 필요한 CUD 메서드에 `@Transactional` 선언이 누락되어 변경 사항이 DB에 반영되지 않거나 롤백이 실패하는가?

### 3. 계층 오염 및 대량 할당(Mass Assignment) 감사
- [ ] Controller에서 DB 엔티티를 파라미터로 직접 바인딩받아 클라이언트가 관리자 필드나 내부 식별자를 위조 주입할 수 있는가?
- [ ] 응답 객체로 Entity가 직접 반환되어 내부 비밀번호 해시, 토큰, 연관 데이터가 외부에 누출되는가?
- [ ] Request DTO에 `@Valid`, `@NotBlank`, `@Size` 등의 입력값 검증이 누락되어 비정상/악의적 페이로드가 유입될 수 있는가?

### 4. 암호학 및 난수 취약점 감사
- [ ] 인증 코드, 세션 키, 일회용 토큰 생성에 예측 가능한 의사 난수(`Math.random()`, `java.util.Random`)가 사용되었는가?
- [ ] 비즈니스 예외 발생 시 `try-catch`로 에러를 삼키거나 200 OK 빈 응답으로 은폐하는 안티패턴이 존재하는가?

---

## 📋 표준 감사 리포트 출력 포맷

감사 수행 시 영혼 없는 칭찬이나 의례적 서론을 일체 배제하고 다음 구조로만 직답합니다:

```markdown
### 🚨 [결함 1: BOLA 취약점] 학생 과제 임의 삭제 가능
- **위치**: `AssignmentService.java#L85-L92`
- **재현 공격 시나리오**: 
  1. 공격자 학생 B가 본인의 세션 쿠키를 유지한 채 `DELETE /assignments/12/submissions/99` (학생 A의 제출물 ID) 요청 전송.
  2. 서버에서 제출물 소유자 검증 없이 `submissionRepository.deleteById(submissionId)`를 바로 실행하여 타인의 과제 파괴 성공.
- **교정 패치 (Fix)**:
  ```java
  Submission submission = submissionRepository.findById(submissionId)
          .orElseThrow(() -> new IllegalArgumentException("제출물을 찾을 수 없습니다: " + submissionId));
  if (!submission.getStudent().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("본인의 제출물만 삭제할 수 있습니다.");
  }
  submissionRepository.delete(submission);
  ```

### ⚡ [성능 병목 1: N+1 쿼리 유발]
- **위치**: `ClassRoomService.java#L140`
- **위험**: 수강생 목록 순회 시 각 수강생의 프로필 조회 쿼리가 N번 추가 실행됨.
- **해결책**: `EnrollmentRepository`에 `@Query("select e from Enrollment e join fetch e.student where ...")` 도입 권장.
```
