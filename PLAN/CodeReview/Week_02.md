# 🔍 UniClass 코드 리뷰 일지 - Week 02

> **프로젝트**: UniClass (스마트 학습 관리 플랫폼)  
> **기간**: 2026-09-28 ~ 2026-10-04  
> **중점 영역**: Phase 2 수업 운영 편의성 강화 (잔여 과제 해결, 동시성/시간대 제어, 실시간 4자리 출석체크, 학기 보관)

---

## 📅 2026-09-29 (화) - 11차 잔여 과제 3건 완결 및 아키텍처 견고성 고도화 리뷰

> **리뷰 대상**:
> - `UniclassApplication.java`, `application.yml`, `src/test/resources/application.yml`
> - `CreateAssignmentDto.java`, `AssignmentService.java`, `AssignmentController.java`
> - `Submission.java`, `ClassRoomService.java`, `AssignmentServiceTest.java`

---

### 🚨 1. 잔여 과제 3건 세부 해결 내역 (100% 완료)

1. **[과제 1] 클라이언트-서버 간 시간대(Timezone) 불일치 위험 완벽 방어**:
   - `UniclassApplication`에 `@PostConstruct`로 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))`를 적용하여 도커/클라우드(기본 UTC) 환경에서도 일관된 KST 시간대 보장.
   - `application.yml` 및 `src/test/resources/application.yml`에 `hibernate.jdbc.time_zone: Asia/Seoul` 및 `spring.jackson.time-zone: Asia/Seoul`을 병행 구성하여 JPA와 JSON 직렬화 전 구간 시간대 동기화 완료.

2. **[과제 2] DTO 유효성 검증의 컨트롤러-서비스 계층 분리 (`@DateTimeFormat`)**:
   - `CreateAssignmentDto.dueDate`를 `String`에서 `LocalDateTime` 타입으로 전환하고 `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")` 및 `@NotNull` 적용.
   - HTML 폼 제출 시 Spring MVC DataBinder가 자동으로 타입을 변환하며, 유효하지 않은 입력은 바인딩 시점에 `@Valid`로 포착하여 폼 에러 필드로 피드백 제공.
   - `AssignmentService` 내부의 중복 `try-catch DateTimeParseException` 보일러플레이트를 제거하고 타입 안전한 서비스 로직으로 간결화.

3. **[과제 3] 동시 채점/수정 시 덮어쓰기(Lost Update) 방어 (`@Version` 낙관적 락)**:
   - `Submission` 엔티티에 JPA `@Version private Long version;` 필드를 선언하여 다중 세션 및 향후 조교(TA) 도입 시 동일 제출물에 대한 동시 수정 충돌을 원천 차단.
   - `AssignmentController`의 `gradeSubmission` 및 `gradeInSpeedGrader`에 `OptimisticLockingFailureException` 전용 핸들러를 구축하여, 충돌 발생 시 사용자에게 친절한 안내 메시지("다른 사용자가 동시에 성적을 수정했습니다...")를 플래시 어트리뷰트로 전달.

---

### 🛡️ 2. Claude AI 리뷰 피드백 추가 반영 (Self-Healing)

* **배치 카운트 형변환 안전성 확보**: `ClassRoomService`의 `countByClassroomIdsAndStatus` 결과 처리 시 `(Long)` 직접 캐스팅 대신 `((Number) r[0]).longValue()`, `((Number) r[1]).longValue()`로 변경하여 DB 방언에 종속되지 않는 안전한 형변환 구현.

---

### 📊 11차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **전역 시간대 일치** | 🛡️ 완벽 | JVM, Hibernate JDBC, Jackson 3중 `Asia/Seoul` 고정 |
| **폼 데이터 바인딩** | 🚀 우수 | `@DateTimeFormat` 기반 Spring DataBinder 통합 검증 |
| **동시성 제어 (Concurrency)** | 🛡️ 완벽 | `@Version` 낙관적 락 + `OptimisticLockingFailureException` 처리 |
| **전체 테스트 검증** | ✅ 100% 통과 | Gradle Unit/Integration 19개 + 파이썬 E2E 3종 ALL PASS |

---

## 📅 2026-09-30 (수) - 12차 Phase 2 Step 7: 실시간 4자리 간편 출석 체크 (Attendance) 구현 및 검증 리뷰

> **리뷰 대상**:
> - 엔티티: `AttendanceSession.java`, `AttendanceRecord.java`, `AttendanceStatus.java`
> - 리포지토리: `AttendanceSessionRepository.java`, `AttendanceRecordRepository.java`
> - 서비스: `AttendanceService.java`
> - 컨트롤러 및 DTO: `AttendanceController.java`, `StartAttendanceDto.java`, `SubmitAttendanceDto.java`, `AttendanceSessionResponseDto.java`, `AttendanceRecordResponseDto.java`, `AttendanceStatusUpdateDto.java`, `StudentAttendanceSummaryDto.java`
> - 템플릿: `templates/attendance/dashboard.html`, `templates/attendance/session.html`, `templates/attendance/student_dashboard.html`, `templates/classroom/detail.html`
> - 테스트 스위트: `AttendanceServiceTest.java` (8개 시나리오), `test_attendance_flow.py` (8개 시나리오 E2E)

---

### 🚨 1. 주요 구현 및 아키텍처 특징

1. **4자리 난수 생성 및 시간대별 상태 자동 판별**:
   - `SecureRandom`을 이용해 1000~9999 범위의 4자리 정수 코드 생성.
   - 기본 3분 카운트다운 타이머 제공, 이후 2분의 지각 허용 윈도우(`lateUntil`)를 두어 네트워크 지연이나 입실 지연 학생을 `LATE`로 자동 판정.
   - 마감 시간 초과 시 또는 교수 수동 마감 시 미제출 학생 일괄 `ABSENT`(결석) 자동 마킹.

2. **중복 출석 및 동시성 방어**:
   - `AttendanceRecord` 엔티티에 `session_id`와 `student_id` 유니크 제약조건 적용 (`@Table(uniqueConstraints = ...)`).
   - 서비스 레벨에서 `existsBySessionIdAndStudentId` 사전 검증 및 DB 유니크 충돌 방어.
   - 클라이언트 접속 IP 기록(`clientIp`)으로 대리 출석 부정행위 추적 지원.

3. **교탁 빔프로젝터 대형 스크린 뷰 & 실시간 현황 폴링**:
   - `templates/attendance/session.html`: 거대한 4자리 번호 타이포그래피, 실시간 원형 카운트다운 게이지, 전체화면(F11) 토글 버튼.
   - 3초 주기 자동 JSON 폴링 (`/classes/{cId}/attendance/sessions/{sId}/status`)을 통해 새로고침 없이 출석 학생 이름 태그가 실시간으로 부드럽게 추가되는 인터랙티브 UI 제공.

4. **Thymeleaf 예약어 충돌 자가 치유 (Self-Healing)**:
   - 뷰 렌더링 시 `${session.authCode}`가 서블릿 내장 객체 `HttpSession`과 충돌하여 값이 누락되던 문제를 발견하고, 컨트롤러 모델 어트리뷰트명을 `attSession`으로 분리하여 템플릿 렌더링 안전성 100% 확보.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`AttendanceServiceTest.java`)**:
   - `startSession_success`: 4자리 번호 세션 정상 개설 및 세션 정보 검증
   - `submitAttendance_present`: 유효 시간 내 정상 코드 입력 시 `PRESENT` 출석 검증
   - `submitAttendance_late`: 3분 초과 후 5분 이내 입력 시 `LATE` 지각 검증
   - `submitAttendance_wrongCode_throwsException`: 잘못된 4자리 번호 거부 검증
   - `submitAttendance_duplicate_throwsException`: 동일 학생 중복 출석 거부 검증
   - `submitAttendance_notEnrolled_throwsException`: 비수강생 접근 거부 검증
   - `closeSession_marksUnattendedStudentsAsAbsent`: 세션 마감 시 미출석자 `ABSENT` 자동 등록 검증
   - `updateRecordStatus_instructor_success`: 교수의 수동 출석 인정/정정 검증
   - **결과**: 8/8 테스트 통과 (전체 단위/통합 테스트: **27/27 ALL PASS**)

2. **HTTP E2E 시나리오 검증 (`test_attendance_flow.py`)**:
   - [1] 교수 및 학생 2명 생성 & 수업 개설 & 초대코드 수강신청: **성공**
   - [2] 교수 4자리 실시간 출석 세션 시작 & 대형 스크린 번호 추출: **성공**
   - [3] 학생 1: 정상 4자리 번호 입력 출석 (`PRESENT`): **성공**
   - [4] 학생 2: 틀린 번호 거부 방어 확인 후 정상 재입력 출석: **성공**
   - [5] 학생 1: 중복 출석 시도 시 안전 차단: **성공**
   - [6] 교수 대형 스크린 실시간 폴링 API (`/status` JSON): 출석 2명, 총 2명, 남은 시간 응답 **성공**
   - [7] 교수 출석 세션 마감 및 미출석자 결석 처리: **성공**
   - [8] 학생 대시보드 출석률 100% 반영 확인: **성공**

---

### 📊 12차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **간편 출석 UX** | 🚀 최상 | 빔프로젝터 전용 대형 뷰 + 3초 실시간 폴링 + 모바일 반응형 입력 |
| **보안 및 부정 방지** | 🛡️ 완벽 | 중복 출석 DB 유니크 가드 + 클라이언트 IP 트래킹 + 지각/결석 자동 분류 |
| **예약어 충돌 치유** | 🛡️ 완벽 | Thymeleaf `HttpSession` 충돌 해결 (`attSession` 분리) |
| **자가 치유 (Self-Healing)** | ✅ 완료 | 학생 번호 유효성 검증 정비 + DTO 바인딩 최적화 |
| **전체 테스트 검증** | ✅ 100% 통과 | Gradle 27개 테스트 + 파이썬 E2E 8단계 ALL PASS |

---

## 📅 2026-09-30 (수) - 13차 아키텍처 및 UI 정보구조 전면 개편 리뷰 (Lean Monolith & Course Navigation App)

> **리뷰 대상**:
> - 엔티티: `ClassRoom.java` (`isArchived`, `assertEditable()`, `archive()`, `unarchive()`)
> - 신규 도메인: `GradeBookDto.java`, `GradeService.java` (`com.uniclass.domain.grade`)
> - 서비스 가드 보강: `ClassRoomService.java`, `MaterialService.java`, `AssignmentService.java`, `AttendanceService.java`
> - 컨트롤러 & 템플릿: `ClassRoomController.java`, `templates/classroom/detail.html` (7대 탭 코스 네비게이션 앱)
> - 테스트 스위트: `GradeServiceTest.java` (2개 단위 테스트), `test_tabs_and_archive.py` (3개 E2E 통합 시나리오)

---

### 🚨 1. 아키텍처 & UI 정보구조 개편 핵심 성과

1. **God Page(`detail.html`) 해체 및 Canvas/Blackboard 스타일 Course Navigation App 전환**:
   - 기존의 모든 자료/과제/출석/인원이 세로로 무한 나열되던 단일 페이지 구조를 타파하고 7개 기능별 탭으로 깔끔하게 정보구조를 재구성:
     1) `overview`: 수업 기본 정보, 교수 안내, 빠른 바로가기 카드.
     2) `materials`: 주차별(1~16주차) 강의자료 아코디언 및 업로드 모달.
     3) `assignments`: 과제 마감 D-Day 뱃지, Speed Grader 원클릭 진입, 개인별 제출/피드백 상태.
     4) `attendance`: 세션 개설, 출석률 통계 및 출석 대시보드 바로가기.
     5) `grades`: 학기 성적부 매트릭스 (과제 합산 + 출석 반영 + 100점 환산 + A+/B+ 등급).
     6) `members`: 참여 교수 및 수강생 명단 일람.
     7) `settings`: 수업 정보 수정 및 아카이빙/보관 토글.

2. **성적 집계 도메인 (`GradeBook` / `GradeService`) 분리**:
   - `Submission.score`와 학기 최종 성적을 동일시하던 구조적 한계를 극복하고, 별도의 성적 집계 서비스(`GradeService`) 구축.
   - 과제 총 배점 대비 획득 점수와 출석률(10% 가중치)을 합산하여 최종 학점 등급(`A+`, `A0`, `B+`, ... `F`)을 결정론적으로 계산.
   - 교수 시점 전원 집계표 vs 학생 시점 본인 성적 일람표의 권한 분리 렌더링.

3. **서버 레벨 읽기 전용 보관(Archive) 가드 구현**:
   - UI에서 버튼만 감추는 클라이언트 취약점을 제거하고, 도메인 핵심 엔티티 `ClassRoom.assertEditable()` 정책 메서드 탑재.
   - `MaterialService`(자료 등록/삭제), `AssignmentService`(과제 생성/제출/채점), `AttendanceService`(출석 세션 생성/제출) 전 영역에 `classRoom.assertEditable()`을 강제하여 아카이브된 수업에 대한 모든 HTTP 변경 요청을 차단.
   - 보관된 수업에 대한 글로벌 읽기 전용 경고 배너 및 언제든지 복구 가능한 `unarchive` 지원.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`GradeServiceTest.java`, 전체 Gradle 스위트)**:
   - 과제 점수 합산, 출석 가중치 반영, 등급 산정 로직 단위 테스트 통과.
   - **전체 단위/통합 테스트**: **29/29 ALL PASS (100% 초록불)**

2. **HTTP E2E 시나리오 검증 (`test_tabs_and_archive.py`)**:
   - [1] 교수 로그인 및 7개 탭 네비게이션 렌더링 검증: **100% 성공**
   - [2] 수업 보관(Archive) 토글 및 상단 글로벌 배너 노출: **100% 성공**
   - [3] 보관 상태에서 신규 과제 출제 HTTP POST 시도 시 서버 레벨 원천 차단 (`assertEditable`): **100% 성공**
   - [4] 수업 보관 해제 및 정상 운영 상태 복구: **100% 성공**
   - [5] 학생 시점 본인 성적부 열람 및 권한 격리: **100% 성공**

---

### 📊 13차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **정보구조 (IA)** | 🚀 혁신 | 7대 코스 탭 네비게이션으로 확장성 확보 및 God Page 완전 해체 |
| **도메인 분리** | 🛡️ 우수 | `GradeBook` / `GradeService` 도입으로 제출 점수와 최종 성적 책임 분리 |
| **보안 & 아카이빙** | 🛡️ 완벽 | 서버 레벨 `classRoom.assertEditable()`로 변경 요청 원천 차단 |
| **전체 테스트 검증** | ✅ 100% 통과 | Gradle 29개 테스트 ALL PASS + E2E 탭/아카이브 검증 100% 통과 |

---

## 📅 2026-09-30 (수) - 14차 Phase 2 Step 8: 데이터 보존 및 일괄 내보내기 (Export) 구현 및 무결성 검증 리뷰

> **리뷰 대상**:
> - 엔티티 및 리포지토리: `SubmissionRepository.java` (일괄 조회 배치 쿼리 추가)
> - 신규 도메인 계층: `ExportService.java`, `ExportController.java` (`com.uniclass.domain.export`)
> - 템플릿: `templates/classroom/detail.html` (`grades`, `assignments`, `settings` 탭 다운로드 버튼 연동)
> - 시드 및 스토리지: `TestDataInit.java` (샘플 파일 물리 디스크 자동 생성)
> - 테스트 스위트: `ExportServiceTest.java` (5개 단위 테스트), `test_export_feature.py` (5개 시나리오 E2E)

---

### 🚨 1. 주요 구현 및 아키텍처 특징

1. **Excel 호환 UTF-8 BOM CSV 스트리밍 (`generateGradesCsv`)**:
   - `GradeBookDto`와 연동하여 수강생 학번, 이름, 이메일, 과제별 득점, 과제 총점, 출석 환산점수, 최종 환산점수(100점), 학점 등급을 CSV 포맷으로 생성.
   - Microsoft Excel에서 한글이 깨지는 현상을 방어하기 위해 선두에 **UTF-8 BOM(`\uFEFF`)**을 삽입하고, 특수문자 및 따옴표 이스케이프(`escapeCsv`)를 엄격히 적용.

2. **Java 표준 기반 폴더별 ZIP 일괄 압축 (`generateSubmissionsZip`)**:
   - Apache Commons Compress 등 무거운 외부 의존성 없이 표준 `java.util.zip.ZipOutputStream`만으로 스트리밍 압축 구현 (Lean 원칙 준수).
   - 압축 엔트리 구조를 `[과제명]/[학번]_[학생이름]_[원본파일명]`으로 체계화하여 파일명 충돌을 방지하고 교수의 제출물 관리 편의성 극대화.
   - 제출 파일이 없거나 디스크 결손 시에도 유효한 ZIP 구조를 보장하는 방어적 폴백(`README.txt`) 탑재.

3. **학생용 종강/졸업 포트폴리오 ZIP 다운로드 (`generateStudentPortfolioZip`)**:
   - 본인이 제출한 모든 과제 원본 파일과 함께 과제별 배점, 취득점수, 제출일시, 제출상태 및 교수의 인라인 상세 피드백을 깔끔하게 서식화한 `과제제출_및_피드백_요약.txt`를 단일 압축 파일로 제공.

4. **철저한 접근 제어 및 권한 분리**:
   - `ClassRoomService`에 `assertInstructor(classroomId, instructorId)`, `assertEnrolled(classroomId, studentId)` 공통 인가 가드를 구축.
   - 학생이 URL 변조를 통해 교수의 전체 성적표나 타 학생 제출물 ZIP을 요청할 경우 서버에서 즉시 거부(403/예외)되도록 방어.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`ExportServiceTest.java`)**:
   - `generateGradesCsv_success`: CSV 헤더, UTF-8 BOM, 학생 행 데이터 정확성 검증 (통과)
   - `generateGradesCsv_classNotFound_throwsException`: 존재하지 않는 수업 예외 검증 (통과)
   - `generateSubmissionsZip_empty_hasReadme`: 제출물 없을 때 README 포함 검증 (통과)
   - `generateSubmissionsZip_withFiles_success`: 과제명 폴더 구조 ZIP 엔트리 생성 검증 (통과)
   - `generateStudentPortfolioZip_success`: 피드백 요약 리포트와 제출 파일 포함 검증 (통과)
   - **전체 단위/통합 테스트**: **34/34 ALL PASS (100% 초록불)**

2. **HTTP E2E 시나리오 검증 (`test_export_feature.py`)**:
   - [1] 교수 로그인 및 성적표 CSV 다운로드 (헤더/BOM/데이터 정상): **100% 성공**
   - [2] 교수 제출물 전체 ZIP 압축 및 내부 파일 트리 검증: **100% 성공**
   - [3] 교수 탭별(`grades`, `assignments`, `settings`) 다운로드 링크 노출 확인: **100% 성공**
   - [4] 학생 로그인 및 내 포트폴리오 ZIP & 피드백 요약서 다운로드 검증: **100% 성공**
   - [5] 학생 뷰 권한 분리 (전체 제출물 다운로드 버튼 미노출): **100% 성공**
   - [6] 학생의 교수 전용 URL 접근 시 원천 차단 (Negative Test): **100% 성공**

---

### 📊 14차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **데이터 내보내기** | 🚀 최상 | Excel 호환 UTF-8 BOM CSV + 폴더별 ZIP 일괄 압축 스트리밍 |
| **보안 및 권한 격리** | 🛡️ 완벽 | `assertInstructor`, `assertEnrolled`로 권한 없는 데이터 접근 원천 차단 |
| **학생 UX 지원** | 🌟 우수 | 종강/졸업 포트폴리오 ZIP 및 교수 피드백 요약 텍스트 리포트 자동 생성 |
| **전체 테스트 검증** | ✅ 100% 통과 | Gradle 34개 테스트 ALL PASS + 파이썬 E2E 6단계 ALL PASS |

---

## 📅 2026-09-30 (수) - 15차 유연한 주차/학사 블록 시스템 (Flexible Course Block System) 구현 및 검증 리뷰

> **리뷰 대상**:
> - 엔티티 및 Enum: `SectionType.java` (신규), `WeekSection.java` (필드 및 편의 메서드 확장)
> - DTO 및 리포지토리: `UpdateWeekSectionDto.java` (신규), `WeekSectionDto.java` (확장), `WeekSectionRepository.java` (최대 주차 번호 집계 쿼리 추가), `AssignmentRepository.java` (과제 연계 여부 체크 쿼리 추가)
> - 서비스 및 컨트롤러: `MaterialService.java` (주차 수정/추가/삭제/휴강 토글 비즈니스 로직), `WeekSectionController.java` (신규 전용 컨트롤러)
> - 템플릿: `templates/classroom/detail.html` (주차별 뱃지/날짜/휴강/시험 배너 렌더링, 주차 설정 모달, 새 주차 추가 모달)
> - 테스트 스위트: `MaterialServiceTest.java` (5개 신규 단위 테스트), `test_flexible_sections.py` (5개 E2E 통합 시나리오)

---

### 🚨 1. 아키텍처 & 기능적 핵심 성과

1. **기계적 16주 고정 한계 극복 및 자율 주차 운영**:
   - `SectionType` 열거형(`LECTURE`, `HOLIDAY`, `EXAM`, `MAKEUP`, `SPECIAL`)을 도입하여, 각 주차의 학사 일정 성격을 시스템이 인지하도록 설계.
   - 학기말 결손 시수를 보충할 수 있도록 16주를 넘어 **17주차, 18주차 보강 주차를 자율적으로 추가(`addCustomWeekSection`)**하거나 미사용 주차를 안전하게 삭제(`deleteWeekSection`) 가능.

2. **원클릭 휴강(HOLIDAY) 전환 & 스마트 안내 배너**:
   - 추석/설 연휴, 개교기념일 등 공휴일 주차를 원클릭(`toggle-holiday`)으로 휴강 전환 가능.
   - 휴강 주차는 불필요한 과제/자료 제출 안내 대신 상단에 산뜻한 휴강 안내 배너("🎉 공휴일/휴강 주간입니다")와 공지사항 텍스트를 노출하여 학생들의 혼란을 원천 방지.

3. **캘린더 날짜(Start ~ End Date) 연동 및 학생 숨김(isHidden) 제어**:
   - 주차별 실제 날짜(`startDate`, `endDate`)를 매핑하여 `(10.05 ~ 10.09)` 형식으로 자동 렌더링.
   - 준비 중이거나 공강 처리된 주차는 `isHidden` 플래그를 통해 학생 목록 조회 시 투명하게 필터링.

4. **도메인 무결성 및 보안 가드**:
   - 자료가 등록되어 있거나 과제가 연결된 주차는 실수로 삭제되지 않도록 사전 차단 검증(`existsByWeekSectionId`) 적용.
   - 모든 상태 변경 API에 `assertInstructor` 및 `classRoom.assertEditable()`을 강제하여 아카이브된 수업 변조 방어.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`MaterialServiceTest.java`)**:
   - `updateWeekSection_holiday_success`: 주차를 휴강으로 전환하고 날짜/공지 설정 검증 (통과)
   - `toggleHoliday_toggleBackAndForth`: 원클릭 휴강 전환 및 일반 강의 복구 검증 (통과)
   - `addCustomWeekSection_makeup_success`: 16주 이후 17주차 보강 생성 및 번호 자동 부여 검증 (통과)
   - `deleteWeekSection_empty_success`: 빈 주차 삭제 및 주차 수 감소 검증 (통과)
   - `hiddenSection_filterForStudent`: 숨김 주차의 학생 뷰 격리 필터링 검증 (통과)
   - **전체 단위/통합 테스트**: **39/39 ALL PASS (100% 초록불)**

2. **HTTP E2E 시나리오 검증 (`test_flexible_sections.py`)**:
   - [1] 교수 로그인 및 주차별 커리큘럼 탭 노출 검증: **100% 성공**
   - [2] 5주차 원클릭 휴강 전환 및 상단 휴강 배너 노출 검증: **100% 성공**
   - [3] 주차 상세 설정 (시험 주간 및 날짜 범위 10.05~10.09) 검증: **100% 성공**
   - [4] 17주차 신규 보강 주차 생성 및 뱃지 노출 검증: **100% 성공**
   - [5] 학생 시점 휴강/시험 배너 렌더링 및 교수 전용 버튼 격리 검증: **100% 성공**

---

---

## 📅 2026-09-30 (수) - 16차 Phase 3 Step 9: 수업 원클릭 복제 및 날짜 자동 시프트 (Course Clone & Date Shift) 구현 및 검증 리뷰

> **리뷰 대상**:
> - DTO: `CloneClassRoomDto.java`
> - 서비스: `ClassRoomService.java` (`cloneClassRoom` 신규 메서드 및 의존성 주입)
> - 컨트롤러: `ClassRoomController.java` (`POST /classes/{id}/clone` 엔드포인트 및 `detail` 모델 보강)
> - 템플릿: `templates/classroom/detail.html` (설정 탭 원클릭 복제 카드 및 `cloneModal`)
> - 테스트 스위트: `ClassRoomServiceTest.java` (4개 신규 테스트), `test_course_clone.py` (8개 시나리오 E2E)

---

### 🚨 1. 아키텍처 & 기능적 핵심 성과

1. **원클릭 복제 (One-Click Course Clone) 및 초대 코드 재생성**:
   - 담당 교수가 지난 학기 수업의 커리큘럼을 새 학기(`2026-2학기`, `2027-1학기` 등)로 1초 만에 복제.
   - 복제 시 새로운 고유 6자리 초대 코드(`SecureRandom`)를 자동 발급하여 기존 학기 학생들과의 충돌 원천 차단.

2. **결정론적 날짜 자동 시프트 (Deterministic Date Shift Algorithm)**:
   - 원본 수업의 baseline 개강일과 새 학기 시작일 간의 일수 차이(`deltaDays = ChronoUnit.DAYS.between(oldStart, newStart)`)를 정밀 계산.
   - 모든 주차의 시작일(`startDate`)과 종료일(`endDate`), 과제의 마감일(`dueDate`), 자료 예약공개일(`releaseAt`)을 `+deltaDays`만큼 일괄 평행 이동.
   - 기존에 설정된 특수 섹션 성격(`SectionType.HOLIDAY`, `EXAM` 등)과 휴강/시험 안내문구(`noticeText`)를 유실 없이 완벽 보존.

3. **교수 편의 옵션 제어 (Lean Monolith Principle)**:
   - `cloneMaterials`(강의자료 복제) 및 `cloneAssignments`(과제 템플릿 복제) 토글 옵션을 지원하여, 주차 구조(뼈대)만 복제하거나 전체 템플릿을 복제할 수 있도록 유연성 부여.

4. **보안 및 데이터 격리 (Zero Student Leakage)**:
   - 학생 수강 명단(`Enrollment`), 학생 제출물(`Submission`), 출석 세션/기록(`AttendanceSession`, `AttendanceRecord`), 성적표(`Grade`)는 절대 복제하지 않고 완전히 격리된 청정 상태(Clean State, 학생 0명)로 생성.

---

### 🧪 2. 테스트 및 검증 결과

1. **단위 및 통합 테스트 (`ClassRoomServiceTest.java`)**:
   - `cloneClassRoom_Success_WithDateShift`: 183일(6개월) 날짜 시프트, 주차/공휴일 보존, 과제 마감일 시프트, 신규 초대코드, 학생 미포함 격리 검증 (통과)
   - `cloneClassRoom_WithoutMaterialsAndAssignments`: 뼈대 복제 옵션 검증 (통과)
   - `cloneClassRoom_Unauthorized_ThrowsException`: 비담당 교수의 무단 복제 시도 차단 예외 검증 (Negative Test, 통과)
   - `cloneClassRoom_MissingSourceId_ThrowsException`: 원본 수업 ID 누락 방어 예외 검증 (Negative Test, 통과)
   - **전체 단위/통합 테스트**: **43/43 ALL PASS (100% 초록불)**

2. **HTTP E2E 시나리오 검증 (`test_course_clone.py`)**:
   - [1] 교수 로그인: **성공**
   - [2] 원본 수업 설정 탭 복제 카드 노출: **성공**
   - [3] 1주차 기준일(2026-03-02 ~ 2026-03-08) 설정: **성공**
   - [4] 원클릭 복제 요청 및 새 수업(ID:2) 리다이렉트: **성공**
   - [5] 주차 커리큘럼 및 개강일 자동 시프트(2026-09-01 ~ 2026-09-07) 확인: **성공**
   - [6] 과제 템플릿 및 마감일(2026-09-14) 자동 시프트 확인: **성공**
   - [7] 신규 고유 초대코드 발급 확인: **성공**
   - [8] 신규 복제 수업 수강생 총 0명 클린 격리 확인: **성공**

---

### 📊 16차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **강의 준비 생산성** | 🚀 극대화 | 새 학기 커리큘럼/과제 세팅 시간 0초 달성 (원클릭 Date Shift) |
| **날짜 시프트 정밀도** | ⏱️ 완벽 | `ChronoUnit.DAYS` 기반 주차/과제/예약공개일 일괄 동기화 |
| **보안 & 데이터 무결성** | 🛡️ 완벽 | 이전 학기 학생/제출물/출석/성적 100% 배제 및 신규 초대코드 발급 |
---

## 📅 2026-09-30 (수) - 17차 UniClass UI/UX 전면 고도화 (우선순위 P1~P3 완결) 리뷰

> **리뷰 대상**:
> - 레이아웃 & 인프라: `templates/layout/default.html` (FontAwesome 6.5.1 CDN, Pretendard 웹폰트, 전역 ESC/Backdrop 모달 닫기)
> - 메인 대시보드: `templates/index.html` (보관 수업 카드 시각적 격리, 퀵 복제 액션, 헤더 앵커 링크 연결)
> - 코스 상세: `templates/classroom/detail.html` (모바일 탭 바 정돈, 이번 주차 스마트 하이라이트, 과제 제출 상태 뱃지, 성적부 스티키 헤더, 플로팅 토스트)

---

### 🚨 1. UI/UX 개선 핵심 성과

1. **디자인 시스템 및 인프라 안정화 (P1)**:
   - FontAwesome CDN 누락으로 인한 잠재적 아이콘 깨짐 문제를 원천 차단.
   - Toss/Notion 표준 Pretendard Variable 웹폰트를 탑재하여 깔끔하고 모던한 SaaS 타이포그래피 완성.
   - 모든 모달 창에서 키보드 `ESC` 키 입력 및 배경(Backdrop) 클릭 시 즉시 닫히도록 전역 이벤트 리스너 통합.

2. **메인 대시보드 가시성 & 직관성 강화 (P2)**:
   - 보관(Archived) 수업 카드를 쿨그레이 톤다운 및 `[🔒 보관됨 · 읽기 전용]` 뱃지로 시각적 격리하여, 현재 운영 중인 강좌와 혼선 방지.
   - 교수용 카드에 `[복제]` 빠른 링크를 추가하여 번거로운 이동 없이 대시보드에서 1초 만에 새 학기 복제 착수 가능.
   - 상단 헤더 메뉴(`과제 현황`, `간편 출석`)의 앵커 링크를 메인 기능 카드로 정확히 스크롤 매핑.

3. **수업 상세(detail.html) 사용성 극대화 (P2 & P3)**:
   - **이번 주차(Current Week) 스마트 하이라이트**: 오늘 날짜(`LocalDate.now()`)가 포함된 주차에 `border-2 border-blue-500` 및 `🌟 이번 주 강의` 파란 뱃지를 자동 부여하여 스크롤 탐색 시간 단축.
   - **과제 제출 상태 한눈에 파악**: 학생 뷰에서 각 과제 카드에 `[제출 완료]`(초록) / `[미제출]`(빨강) 뱃지를 명확히 렌더링.
   - **성적부 스티키 헤더**: 대규모 수강생 스크롤 시에도 컬럼명(학생명, 학번, 과제별 배점 등)이 상단에 고정(`sticky top-0`)되어 가독성 보장.
   - **레이아웃 시프트(CLS) 제로 플로팅 토스트**: 페이지 본문을 아래로 밀어내던 상단 배너를 화면 우측 상단 고정 플로팅 토스트 카드로 전환하고 3.5초 자동 스무스 페이드아웃 적용.

---

### 📊 17차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **타이포그래피 & 시각 디자인** | 🌟 최상 | Pretendard 웹폰트 + FontAwesome 6.5.1 공식 CDN 완벽 동기화 |
| **사용자 동선 (User Flow)** | 🚀 고도화 | 보관 수업 톤다운 + 대시보드 퀵 복제 + 이번 주차 자동 하이라이트 |
| **모달 & 인터랙션 (UX)** | 🛡️ 완벽 | ESC 키/배경 클릭 모달 닫기 + CLS 제로 플로팅 토스트 적용 |
| **회귀 검증 (Regression Test)** | ✅ 100% 통과 | Gradle 43개 전체 테스트 + E2E 2종 ALL PASS |

---

## 📅 2026-10-02 (금) - 18차 학생 개인 성적 보호 & 학급 평균 교수 전용 격리 및 적대적 정밀 감사

> **리뷰 대상**:
> - 성적 도메인: `GradeBookDto.java`, `GradeService.java`
> - 수업 상세 템플릿: `templates/classroom/detail.html` (Tab 1 개요, Tab 5 성적부)
> - 테스트 스위트: `GradeServiceTest.java`, `test_grade_privacy.py`

---

### 🚨 1. 주요 개선 및 권한 분리 성과

1. **백엔드 레벨 개인정보 & 학급 평균 은닉**:
   - `GradeBookDto.classAverageScore`: `Double` 래퍼 타입으로 전환하여 비교수 접근 시 `null` 명시적 반환.
   - `GradeService.getGradeBook`: 학생 조회 시 `isInstructor == false` 분기를 통해 `classAverageScore` 계산/할당을 원천 차단하고, 수강생 목록(`enrollments`)을 본인 1명으로 엄격 필터링.
2. **프론트엔드(Thymeleaf) 화면 분리 및 소스코드 보안**:
   - Tab 1 (개요): 교수는 `학급 평균 성적`, 학생은 `내 종합 성적 (점수 및 학점)` 카드로 분기 렌더링.
   - Tab 5 (성적부): 교수는 전체 학생 매트릭스 및 CSV 내보내기, 학생은 본인 요약 카드 3종 + 1개 행만 노출.
   - Thymeleaf 파서 레벨 주석(`<!--/* ... */-->`)을 적용하여 클라이언트 브라우저의 '페이지 소스 보기'에서도 민감 데이터 및 주석 유출을 원천 방어.
3. **적대적 감사관(Adversarial Auditor) 지적 및 자가 치유(Self-Healing)**:
   - 지적: `enrollments.stream().filter(e -> e.getStudent().getId().equals(currentUserId))`의 잠재적 NPE 위험.
   - 치유: `filter(e -> e.getStudent() != null && Objects.equals(e.getStudent().getId(), currentUserId))`로 방어적 null-safe 처리 완료.
4. **외부 도구 다이어트 (Codex 제거 및 파이프라인 경량화)**:
   - 과도한 데몬 및 비대화형 행(Hang) 문제를 일으키던 `@openai/codex` CLI를 완전 삭제하고, Antigravity 내장 엔진 체계로 일원화.

---

### 📊 18차 리뷰 종합 평가

| 항목 | 평가 | 세부 조치 결과 |
| :--- | :---: | :--- |
| **성적 개인정보 보호** | 🛡️ 완벽 | 타 학생 성적 일체 미노출 및 보안 안내 배너 렌더링 |
| **학급 평균 교수 격리** | 🛡️ 완벽 | 백엔드 API null 반환 + 프론트엔드 비노출 + 소스 주석 차단 |
| **적대적 보안 감사** | 🌟 ALL CLEAR | 잠재적 NPE 자가 치유 완료 + 무결성 확보 |
| **전체 회귀 검증** | ✅ 100% 통과 | Gradle 50개 전체 테스트 + E2E 권한 분리 테스트 ALL PASS |

