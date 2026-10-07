# 🔍 UniClass 코드 리뷰 일지 - Week 03

> **프로젝트**: UniClass (스마트 학습 관리 플랫폼)  
> **기간**: 2026-10-05 ~ 2026-10-11  
> **중점 영역**: Phase 3 교수 강의 준비 코파일럿 및 영구 보관함 (My Library, UI/UX 편의 패키지, DTO 검증 강화, 쿼터 제어 파이프라인)

---

## 📅 2026-10-07 (수) - Phase 3 My Library 구축 및 UI/UX 편의 패키지 적대적 감사

> **리뷰 대상**:
> - 엔티티: `LibraryItem.java`, `LibraryCategory.java`, `WeekSection.java`
> - 리포지토리: `LibraryItemRepository.java`, `WeekSectionRepository.java`
> - 서비스: `LibraryService.java`, `FileStorageService.java`, `MaterialService.java`, `ClassRoomService.java`
> - 컨트롤러 & DTO: `LibraryController.java`, `ClassRoomController.java`, `MaterialController.java`, `UpdateWeekSectionDto.java`, `LibraryItemResponseDto.java`
> - 템플릿: `templates/library/index.html`, `templates/classroom/detail.html`, `templates/layout/default.html`
> - 테스트 스위트: `LibraryServiceTest.java` (7개 시나리오), `MaterialServiceTest.java`
> - 파이프라인: `scripts/claude_review_cli.py`, `.git/hooks/pre-commit`, `GEMINI.md`

---

### 🚨 1. 주요 취약점 감사 및 해결 내역 (Self-Healing & Hardening)

1. **[BOLA/IDOR 방어] My Library 교수 개인 자료 격리 및 인가 검증**:
   - **위험**: 타 교수의 `libraryItemId`를 URL/파라미터로 전송하여 타인의 교안을 무단 다운로드하거나 자신의 수업으로 임의 임포트/삭제하는 IDOR 위험.
   - **조치**: `LibraryService` 전 메서드(`getItem`, `deleteItem`, `importToClassroom`)에 `item.getUser().getId().equals(instructorId)` 불변 검증 적용. 위반 시 즉각 `IllegalArgumentException` Fail-Fast 차단.

2. **[Mass Assignment 방어] DTO 분리 및 `@Valid` 유효성 검증**:
   - **위험**: 주차 설정(`UpdateWeekSectionDto`) 수정 시 `startDate`가 `endDate`보다 늦은 날짜 역전 현상, 비정상적으로 긴 텍스트 입력으로 인한 뷰 깨짐/DB 오류.
   - **조치**: 
     - DTO에 `@Size(max = 100)`, `@Size(max = 2000)` 제약 부여.
     - `MaterialService.updateWeekSection()`에서 `dto.getStartDate().isAfter(dto.getEndDate())` 검증 로직 추가 및 명시적 예외 발생.
     - `MaterialController`에서 `@Valid UpdateWeekSectionDto` 및 `BindingResult` 검증 바인딩.

3. **[스토리지 무결성] 파일 물리 복제(`copy`) 분리**:
   - **위험**: 보관함 자료를 수업으로 임포트할 때 같은 파일 경로를 공유하면, 수업 자료를 삭제하거나 보관함 자료를 삭제할 때 상호 참조 파괴(Dangling Pointer) 발생.
   - **조치**: `FileStorageService.copy()`를 신설하여 보관함 디렉터리(`materials/library/`)에서 대상 수업 디렉터리(`materials/class_{classroomId}/`)로 UUID 기반 새 파일을 물리 복제한 후 `Material` 엔티티를 생성하도록 영구 격리.

4. **[리뷰 파이프라인 토큰 폭주 방어] Pre-commit 훅 디커플링 및 Cap=1 강제**:
   - **원인 분석**: Git commit 시 청크별로 반복 호출되던 Claude API가 대용량 diff 시 단 1~2회 커밋만에 2시간 롤링 쿼터를 고갈시킴. 클래스 헤더 컨텍스트 중복 주입으로 인한 토큰 기하급수적 낭비.
   - **조치**: 
     - Pre-commit 훅은 물리적 품질 게이트인 `.\gradlew.bat test` (결정론적 검증)만 수행.
     - Claude 감사는 온디맨드로 분리하고, 호출 횟수를 무조건 단 1회(`Cap = 1`)로 상한 강제.
     - 리뷰 대상 diff를 Security/Controller/Service 핵심 파일 우선순위로 자동 압축.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`LibraryServiceTest.java` & 전체 스위트)**:
   - `uploadItem_success`: 보관함 업로드 및 파일 저장 정상 검증
   - `deleteItem_success`: 본인 자료 정상 삭제 검증
   - `deleteItem_unauthorized_throwsException`: 타인 자료 삭제 시 BOLA 예외 차단 검증
   - `getItem_unauthorized_throwsException`: 타인 자료 조회/다운로드 시 BOLA 예외 차단 검증
   - `importToClassroom_success`: 보관함 자료를 대상 수업 주차 자료로 물리 복제 임포트 검증
   - `importToClassroom_notClassInstructor_throwsException`: 타인 수업으로 임포트 시도 시 인가 차단 검증
   - `importToClassroom_notItemOwner_throwsException`: 타인 보관함 자료 임포트 시도 시 인가 차단 검증
   - **결과**: `LibraryServiceTest` 7/7 ALL PASS (전체 통합 테스트: **59/59 ALL PASS**)

---

### 📊 종합 감사 평가

| 영역 | 감사 항목 | 상태 | 세부 평가 |
| :--- | :--- | :---: | :--- |
| **보안 (Security)** | BOLA/IDOR 방어 | 🛡️ 무결 | 세션 유저와 리소스 소유권 불일치 시 Fail-Fast 철저 |
| **무결성 (Integrity)**| 파일 격리 및 물리 복제 | 🛡️ 무결 | `FileStorageService.copy` 통한 상호 참조 파괴 원천 차단 |
| **데이터 검증** | DTO 유효성 & 날짜 역전 방어 | 🚀 양호 | `@Valid` 바인딩 및 `startDate <= endDate` 서비스 방어 |
| **동시성 & 트랜잭션** | 읽기/쓰기 분리 | 🚀 양호 | `@Transactional(readOnly = true)` 및 CUD 분리 |
| **회귀 검증** | Gradle Test Suite | ✅ 100% | 59/59 단위/통합 테스트 초록불 |
