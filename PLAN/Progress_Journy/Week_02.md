# 📅 Progress Journey - Week 02

> **기간**: 2026-09-28 ~ 2026-10-04  
> **주요 목표**: Phase 2 수업 운영 편의성 강화 (아키텍처 견고성 확보, 실시간 간편 출석체크, 학기 종료/보관 라이프사이클)

---

### 📝 2026-09-29 (화)

#### 1. 진행 내용 (What was done)
* **Week 1 리뷰 잔여 과제 3건 완결 (아키텍처 및 데이터 무결성 강화)**:
  * **[과제 1] 전역 시간대 KST(`Asia/Seoul`) 동기화**:
    * `UniclassApplication`: `@PostConstruct`로 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))` 강제.
    * `application.yml` & `src/test/resources/application.yml`: `hibernate.jdbc.time_zone` 및 `spring.jackson.time-zone` 일괄 구성.
  * **[과제 2] 컨트롤러-서비스 간 DTO 유효성 검증 계층 분리**:
    * `CreateAssignmentDto.dueDate`를 `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")` 기반 `LocalDateTime`으로 전환.
    * 서비스 레이어의 중복 날짜 파싱 예외 로직 제거.
  * **[과제 3] 동시 채점/수정 시 덮어쓰기 방어 (`@Version` 낙관적 락)**:
    * `Submission` 엔티티에 JPA `@Version private Long version;` 필드 추가.
    * `AssignmentController`에 `OptimisticLockingFailureException` 전용 플래시 메시지 피드백 구현.
* **Claude AI 리뷰 피드백 자가 치유(Self-Healing)**:
  * `ClassRoomService.countByClassroomIdsAndStatus` 결과 처리 시 `((Number) r[0]).longValue()` 형태의 안전한 형변환 적용.
* **테스트 검증**:
  * Gradle 전체 테스트 19/19 통과 및 스피드그레이더/과제제출 E2E 검증 완료.

---

### 📝 2026-09-30 (수)

#### 1. 진행 내용 (What was done)
* **Phase 2 - 7단계: 실시간 4자리 간편 출석 체크 (Attendance) 구현 완료**:
  * **도메인 엔티티 & 레포지토리 설계**:
    * `AttendanceSession`: `SecureRandom` 4자리 인증번호, 3분 유효시간 및 2분 지각 허용 윈도우.
    * `AttendanceRecord`: `(session_id, student_id)` 복합 유니크 제약조건 적용, 접속 IP(`clientIp`) 로깅.
    * `AttendanceStatus`: `PRESENT`, `LATE`, `ABSENT`, `EXCUSED`.
  * **비즈니스 서비스 (`AttendanceService`)**:
    * 세션 생성, 실시간 제출 판별(출석/지각/초과), 중복 제출 방어, 세션 종료 시 미제출자 `ABSENT` 일괄 등록.
  * **컨트롤러 & 뷰 계층 (`AttendanceController`)**:
    * 교수용 대시보드 (`/classes/{id}/attendance`) 및 세션 개설.
    * 빔프로젝터 대형 스크린 팝업 (`/classes/{id}/attendance/sessions/{id}`): F11 전체화면 지원, 거대 4자리 타이포그래피, 3초 주기 실시간 JSON 폴링 (`/status`).
    * 학생용 출석 대시보드 및 4자리 번호 입력 폼.
* **디버깅 및 자가 치유 (Self-Healing)**:
  * Thymeleaf 내장 예약어(`${session}`) 충돌 문제 해결 ➡️ 모델 변수 `attSession` 분리 적용.
  * 학생 학번 정규식(`^[0-9A-Za-z]{4,20}$`) 준수 및 로그인 인증 흐름 안정화.
* **테스트 검증**:
  * `AttendanceServiceTest`: 8대 시나리오 단위/통합 테스트 ALL PASS (전체 27개 테스트 100% 통과).
  * `test_attendance_flow.py`: 교수 개설 ➡️ 대형스크린 번호 추출 ➡️ 학생1 정상 출석 ➡️ 학생2 오답 방어 및 정상 출석 ➡️ 중복 방어 ➡️ 실시간 폴링 ➡️ 세션 마감 ➡️ 출석률 100% 반영 전 과정 통과.
* **문서 동기화**:
  * `PLAN/TODO.md` Step 7 전 항목 완료(`[x]`) 체크.

* **아키텍처 및 UI 정보구조 전면 재설계 (Architectural Overhaul & Lean Monolith)**:
  * **[개선 1] God Page 해체 및 탭 기반 코스 네비게이션 앱 (`detail.html`) 구현**:
    * 기존 거대 단일 상세 페이지를 Canvas/Blackboard 스타일의 7개 코스 탭으로 분리:
      - `overview` (수업 개요, 최근 공지/활동, 빠른 링크)
      - `materials` (주차별 강의자료 및 주차 섹션 아코디언)
      - `assignments` (과제 목록, D-day 뱃지, Speed Grader 링크, 개인 제출 현황)
      - `attendance` (출석 통계 및 간편 출석체크 링크)
      - `grades` (성적부 일람표: 과제 총점, 출석 반영 점수, 총점 및 Letter Grade)
      - `members` (참여 교수 및 수강생 명단)
      - `settings` (수업 기본 정보 수정 및 아카이빙/보관 토글)
  * **[개선 2] 성적 집계 도메인 (`GradeBook` / `GradeService`) 신설**:
    * `Submission.score`와 학기 최종 성적의 도메인 책임을 분리.
    * `GradeService`: 과제 총점 + 출석 환산 점수(10점 만점 기준)를 합산하여 100점 만점 환산 점수와 학점 등급(`A+`, `A0`, `B+` ... `F`)을 결정론적으로 계산.
    * 교수 시점: 수강생 전원 종합 성적 매트릭스 표 렌더링.
    * 학생 시점: 본인 점수, 출석률, 종합 점수 및 등급만 안전하게 열람.
  * **[개선 3] 서버 레벨 읽기 전용 보관(Archive) 가드 구현**:
    * UI 레벨 버튼 숨김 취약점을 방어하기 위해 도메인 엔티티(`ClassRoom.assertEditable()`)에 상태 가드 탑재.
    * 모든 변경 서비스(`MaterialService.uploadMaterial`, `MaterialService.deleteMaterial`, `AssignmentService.createAssignment`, `AssignmentService.submitAssignment`, `AssignmentService.gradeSubmission`, `AssignmentService.gradeStudentInSpeedGrader`, `AttendanceService.startSession`, `AttendanceService.submitAttendance`)에서 `classRoom.assertEditable()`을 강제하여 아카이브된 수업에 대한 HTTP POST 위변조 시도를 `IllegalStateException`으로 원천 차단.
    * `POST /classes/{id}/archive`, `POST /classes/{id}/unarchive` 엔드포인트 지원 및 상단 글로벌 경고 배너 렌더링.
* **테스트 및 검증**:
  * `GradeServiceTest`: 과제 점수 합산, 출석률 가중치 계산, 등급 산정 단위 테스트 작성 및 통과.
  * Gradle 전체 테스트 29/29 통과 (초록불 100%).
  * E2E 자동화 검증 (`test_tabs_and_archive.py`):
    - 교수/학생 7개 탭 렌더링 검증 100% OK
    - 수업 보관 토글 시 과제 등록/수정/출석 차단 및 보관 해제 복구 검증 100% OK
    - 학생 성적 탭 개인정보 격리 검증 100% OK

* **Phase 2 - 8단계: 데이터 보존 및 일괄 내보내기 (Export) 구현 완료 (Phase 2 100% 완결 🎉)**:
  * **[기능 1] 교수용 학기 종합 성적표 CSV 내보내기 (`ExportService.generateGradesCsv`)**:
    * `GradeBook` 매트릭스 데이터를 기반으로 학번, 이름, 이메일, 과제별 점수, 과제 총점, 출석률, 종합 점수, 학점 등급을 스트리밍.
    * Microsoft Excel 한글 깨짐을 원천 방지하기 위해 **UTF-8 BOM(`\uFEFF`)** 포함.
  * **[기능 2] 교수용 전체 과제 제출물 폴더별 일괄 ZIP 다운로드 (`ExportService.generateSubmissionsZip`)**:
    * `[과제명]/[학번]_[학생이름]_[원본파일명]` 구조의 체계적인 디렉토리 압축.
    * 외부 라이브러리 없이 Java 표준 `ZipOutputStream`으로 가볍고 안전하게 스트리밍.
  * **[기능 3] 학생용 종강/졸업 과제 포트폴리오 ZIP 다운로드 (`ExportService.generateStudentPortfolioZip`)**:
    * 본인이 제출한 원본 파일들과 과제별 배점, 득점, 제출일시, 상태, 교수 상세 피드백을 수록한 `과제제출_및_피드백_요약.txt` 리포트 일괄 압축.
  * **[기능 4] 보안 및 권한 분리 (`ExportController`)**:
    * `classRoomService.assertInstructor`, `classRoomService.assertEnrolled`를 통해 비인가자의 URL 직접 접근 원천 차단.
    * `grades`, `assignments`, `settings` 탭에 전용 다운로드 버튼 UI 연동.
* **테스트 및 검증**:
  * `ExportServiceTest`: CSV 헤더/BOM/데이터, 제출물 ZIP, 학생 포트폴리오 ZIP 및 예외 케이스 등 단위 테스트 작성 완료.
  * Gradle 전체 테스트 34/34 통과 (초록불 100%).
  * E2E 자동화 검증 (`test_export_feature.py`):
    - 교수 성적표 CSV 다운로드 및 내용 검증 100% OK
    - 교수 제출물 전체 ZIP 압축 및 내부 파일 목록 검증 100% OK
    - 학생 포트폴리오 ZIP 및 피드백 텍스트 파일 검증 100% OK
    - 학생의 교수 전용 내보내기 URL 직접 접근 시 403/500 원천 차단 100% OK

* **유연한 주차/학사 블록 시스템 (Flexible Course Block System) 구현 완료**:
  * **[배경 및 문제 해결]**: 16주 기계적 고정 한계를 탈피하여 공휴일(추석/연휴), 시험(중간/기말), 보강, 계절학기 등 실제 대학 학사 일정의 변수를 수용.
  * **[엔티티 및 도메인 확장]**:
    * `SectionType` Enum 신설: `LECTURE`(일반 강의), `HOLIDAY`(휴강/공휴일), `EXAM`(시험 주간), `MAKEUP`(보강 주차), `SPECIAL`(특강/발표).
    * `WeekSection`: `sectionType`, `startDate`, `endDate`, `noticeText`, `isHidden` 필드 추가.
    * 비즈니스 편의 메서드: `markAsHoliday()`, `markAsLecture()`, `updateSectionInfo()`.
  * **[서비스 및 컨트롤러 계층 (`WeekSectionController`, `MaterialService`)]**:
    * `POST /classes/{cId}/sections/{sId}/toggle-holiday`: 원클릭 휴강 전환 / 강의 복구 토글.
    * `POST /classes/{cId}/sections/{sId}/update`: 주차 유형, 기간(캘린더 날짜), 휴강 공지 문구, 숨김 여부 수정.
    * `POST /classes/{cId}/sections/new`: 16주 한계를 넘어서는 보강/특강 주차(+17W, +18W...) 자율 추가.
    * `POST /classes/{cId}/sections/{sId}/delete`: 자료/과제가 없는 빈 주차 안전 삭제.
    * `assertEditable()` 및 `assertInstructor()`를 통한 엄격한 권한 및 아카이브 가드 유지.
  * **[UI/UX 개선 (`materials` 탭)]**:
    * 주차 헤더에 시각적 뱃지(휴강/시험/보강/특강) 및 날짜 범위(`03.02 ~ 03.08`) 렌더링.
    * 휴강/시험 주간 시 상단에 산뜻한 전용 공지 안내 카드 노출.
    * 학생 시점: 숨김(`isHidden`) 처리된 주차 자동 필터링 및 관리 버튼 은닉.
    * 교수 시점: 원클릭 휴강 토글, 주차 상세 설정 모달, 새 주차 추가 모달 제공.
* **테스트 및 검증**:
  * `MaterialServiceTest`: 휴강 전환, 날짜/공지 수정, 17주차 보강 추가, 주차 삭제, 학생 숨김 필터링 등 단위 테스트 5종 추가 완료 (총 39개 테스트 100% 통과).
  * E2E 자동화 검증 (`test_flexible_sections.py`):
    - 교수 원클릭 휴강 전환 및 상단 배너 노출 100% OK
    - 시험 주간 및 캘린더 날짜(10.05 ~ 10.09) 설정 100% OK
    - 17주차 신규 보강 주차 자율 추가 100% OK
    - 학생 뷰에서 시험/휴강 일정 정확한 노출 및 관리자 버튼 권한 격리 100% OK

* **Phase 3 - Step 9: 수업 원클릭 복제 및 날짜 자동 시프트 (Course Clone & Date Shift) 구현 완료**:
  * **[설계 배경 및 목적]**:
    * 매 학기 반복되는 주차 커리큘럼 작성, 강의 자료 업로드, 과제 템플릿(배점/설명/지각정책) 등록의 번거로움을 해결.
    * 이전 학기(또는 작년 동학기) 수업의 구조를 1초 만에 새 학기로 복제하고, 새 개강일 입력 시 모든 주차 일정과 과제 마감일을 자동 계산(`Date Shift`).
  * **[핵심 구현 사항]**:
    * **DTO 계층 (`CloneClassRoomDto`)**:
      * `sourceClassroomId`, `newName`, `newSemester`, `newDivision`, `newStartDate`, `cloneMaterials`, `cloneAssignments` 정의 및 유효성 검증.
    * **비즈니스 계층 (`ClassRoomService.cloneClassRoom`)**:
      * 담당 교수 소유권 인가 검증 (`assertInstructor`).
      * 새 고유 6자리 초대 코드 발급 및 복제 수업 엔티티 생성.
      * **Date Shift 알고리즘**: 원본 주차의 baseline 시작일과 새 개강일 간의 `deltaDays`(ChronoUnit.DAYS)를 계산하여, 모든 주차(`startDate`, `endDate`)와 과제(`dueDate`), 자료 예약공개일(`releaseAt`)을 일괄 시프트.
      * **커리큘럼 및 템플릿 심층 복제**: 특수 섹션 성격(`SectionType.HOLIDAY`, `EXAM` 등) 및 안내문구(`noticeText`) 보존, 자료/과제 템플릿 복제.
      * **데이터 무결성 & 보안 격리**: 이전 학기 학생 수강 명단(`Enrollment`), 제출물(`Submission`), 출석부(`AttendanceRecord`), 성적(`Grade`)은 일체 복제하지 않고 깨끗한 상태로 격리.
    * **컨트롤러 & 뷰 계층 (`ClassRoomController`, `detail.html`)**:
      * `POST /classes/{id}/clone` 엔드포인트 추가.
      * 설정(`settings`) 탭에 '새 학기 수업으로 복제 (Date Shift)' 전용 카드 및 원클릭 복제 모달(`cloneModal`) 탑재.
  * **[테스트 및 검증]**:
    * `ClassRoomServiceTest`:
      - 183일(6개월) 시프트 및 주차/자료/과제 마감일 정확성 검증.
      - 옵션 제외(자료/과제 미복제 뼈대 복제) 검증.
      - 타 교수 무단 복제 시도 차단(Negative Test) 검증.
      - 원본 수업 ID 누락 방어(Negative Test) 검증.
    * E2E 실시간 브라우저 시뮬레이션 (`test_course_clone.py`):
      - 로그인 ➡️ 1번 수업 설정 탭 ➡️ 1주차 기준일 설정 ➡️ 원클릭 복제 요청 ➡️ 새 수업(ID:2) 리다이렉트 ➡️ 1주차 개강일(2026-09-01) 자동 시프트 확인 ➡️ 과제 마감일(2026-09-14) 자동 시프트 확인 ➡️ 신규 초대코드 발급 확인 ➡️ 수강생 0명 클린 격리 확인 (100% ALL PASS).
    * 전체 Gradle 테스트: **43/43 ALL PASS (0 Failures, 0 Errors)**.

* **UniClass UI/UX 전면 고도화 (우선순위 P1~P3 완결)**:
  * **[P1. 기초 인프라 및 디자인 시스템]**:
    * `layout/default.html`에 FontAwesome 6.5.1 공식 CDN 추가 (아이콘 깨짐 원천 방어).
    * Toss/Notion 표준 Pretendard Variable 웹폰트 탑재 및 Tailwind fontFamily 확장 (프리미엄 한글 타이포그래피 구현).
    * 전역 모달 키보드 `ESC` 키 및 외부 어두운 배경(Backdrop) 클릭 시 자동 닫기 이벤트 리스너 통합 구축.
  * **[P2. 메인 대시보드 및 코스 상세 UX 강화]**:
    * `index.html`: 보관(Archived) 수업 카드 시각적 격리 (쿨그레이 톤다운, `[🔒 보관됨 · 읽기 전용]` 뱃지, 교수용 원클릭 `[복제]` 빠른 링크 탑재).
    * `index.html`: 헤더 네비게이션 앵커 링크(`assignments`, `attendance`) 연결 및 스크롤 최적화.
    * `detail.html`: 7개 탭 바 모바일 수평 스크롤 UI 정돈 (`scrollbar-none`, `divide-x`).
    * `detail.html`: 주차별 자료실(Materials)에서 현재 주차(`LocalDate.now()`) 자동 판별 및 **`🌟 이번 주 강의`** 파란 링/뱃지 스마트 하이라이트.
    * `detail.html`: 과제(Assignments) 탭에서 학생 시점 본인 제출 상태(`[제출 완료]` / `[미제출]`) 컬러 뱃지 노출.
    * `detail.html`: 성적부(Grades) 데이터 테이블 상단 스티키 헤더(`sticky top-0 backdrop-blur-sm`) 고정.
  * **[P3. 레이아웃 시프트(CLS) 제로 플로팅 토스트]**:
    * 상단 배너가 컨텐츠를 아래로 밀어내는 CLS 문제를 해소하기 위해 화면 우측 상단 고정 플로팅 토스트 카드 전환 및 3.5초 자동 스무스 페이드아웃 적용.
  * **[테스트 및 검증]**:
    * 전체 Gradle 테스트: **43/43 ALL PASS (100% 초록불)**.
    * E2E 실시간 브라우저 시뮬레이션 (`test_course_clone.py`, `test_flexible_sections.py`): **100% ALL PASS**.

* **스마트 캠퍼스 포털 & 모바일 앱 런처 (`/`) 및 회원 전용 클래스룸 앱 (`/classes`) 정보구조(IA) 분리 개편 (2026-10-01)**:
  * **[배경 및 설계 의도]**:
    - 기존 메인 페이지(`/`)가 곧바로 내 수업 목록 서랍으로 렌더링되어, 비회원이나 서비스 첫 진입 시 포털다운 개방감과 다양한 기능 접근성이 부족했던 구조적 한계 개선.
    - 스마트폰 홈 화면에서 여러 어플리케이션을 터치해 진입하는 모바일 앱 런처 멘탈 모델을 캠퍼스 플랫폼에 투영.
  * **[1. 스마트 캠퍼스 포털 홈 (`GET /`, `index.html`)]**:
    - **공통 학사 캘린더**: 2026학년도 1학기 8대 주요 학사 마일스톤(개강, 수강정정, 중간고사, 어린이날 휴강, 대동제 축제, 보강주간, 기말고사, 종강)을 타임라인 리스트 및 블록 유형 범례로 렌더링.
    - **비로그인 사용자**: 플러스(+) 형태의 대형 점선 위젯 카드를 노출하여, 로그인 시 즉시 활성화될 캠퍼스 도구 생태계를 직관적으로 안내.
    - **로그인 사용자 (교수/학생)**: 스퀘어클(Squircle) 모바일 앱 런처 그리드 노출:
      - 📚 **클래스룸**: 내 수업 관리 및 주차별 강의실 (`/classes`)
      - ⚡ **간편 출석**: 4자리 실시간 인증 및 출석부
      - ✍️ **스피드 그레이더**: 문서 뷰어 & 루브릭 채점기
      - 📊 **성적 & 포트폴리오**: 종합 성적표 및 ZIP/CSV 내보내기
      - 🤖 **AI 코파일럿**: 강의계획서 & 퀴즈 생성기 (Phase 3 준비)
      - ➕ **앱 추가**: 커스텀 바로가기 위젯
  * **[2. 회원 전용 클래스룸 앱 (`GET /classes`, `classroom/list.html`)]**:
    - 비인증 접근 시 Spring Security가 자동으로 로그인 페이지(`/login`)로 302 리다이렉트.
    - **수업 상태 필터 탭 바**: `전체 강좌`, `운영 중`, `보관됨` 3단계 필터 및 실시간 건수 뱃지(`totalCount`, `activeCount`, `archivedCount`).
    - **사용자 역할별 CTA 버튼**: 교수(`새 수업 개설`), 학생(`초대 코드로 참가`), 보관 수업 원클릭 `[복제]` 빠른 링크.
    - **공통 네비게이션 동기화 (`layout/default.html`)**: 상단 헤더 메뉴를 `캠퍼스 포털(/)`, `클래스룸(/classes)`, `학사 캘린더(/#academic-calendar)`로 일원화.
  * **[테스트 및 검증]**:
    - `ClassRoomControllerTest`:
      - 비로그인 포털 캘린더 8개 이벤트 렌더링 검증.
      - 로그인 교수 포털 강좌 수 및 모델 전달 검증.
      - 비인증 클래스룸 접근 시 302 리다이렉트 검증.
      - 교수/학생 클래스룸 필터(`all`, `active`, `archived`) 및 카운트 검증.
      - 학생의 수업 개설(`/classes/new`) 접근 403 Forbidden 권한 격리 검증.
    - Gradle 전체 테스트: **49/49 ALL PASS (100% 초록불)**.
    - 실시간 브라우저 E2E 검증 통과 (익명 포털 캘린더, 학생 앱 런처 & 클래스룸 필터, 교수 개설 & 복제).

* **홈 화면 모바일 앱 런처 옵션 A(간편 모달 런처) 적용 및 AI 코파일럿 제외 (2026-10-02)**:
  * **[사용자 피드백 반영]**:
    - 홈 화면에서 `간편 출석`, `스피드그레이더`, `성적 & 포트폴리오` 클릭 시 단순히 `/classes`로 이동하던 구조를 개선하여, 실제 기능으로 즉시 연결되는 팝업 모달 UX(옵션 A) 적용.
    - 미개발 상태인 AI 코파일럿 카드를 홈 화면 런처 및 미리보기에서 완전히 제외하고 4대 핵심 앱으로 대칭형 4열 그리드(`grid-cols-2 sm:grid-cols-4`) 재정비.
  * **[간편 모달 런처 (Quick App Launcher Modal)]**:
    - **⚡ 간편 출석**: 클릭 시 모달 오픈 ➡️ 원하는 강좌 선택 시 `/classes/{id}/attendance`로 1초 만에 다이렉트 진입.
    - **✍️ 스피드그레이더**: 클릭 시 모달 오픈 ➡️ 원하는 강좌 선택 시 `/classes/{id}?tab=assignments`로 다이렉트 진입.
    - **📊 성적 & 포트폴리오**: 클릭 시 모달 오픈 ➡️ 원하는 강좌 선택 시 `/classes/{id}?tab=grades`로 다이렉트 진입.
    - 수업 미등록 시 친절한 빈 상태(Empty State) 카드 및 클래스룸 바로가기 제공.
    - 키보드 `ESC` 키 및 외부 배경(Backdrop) 클릭 자동 닫기 지원.
  * **[테스트 및 검증]**:
    - Gradle 전체 빌드 및 테스트: **49/49 ALL PASS (100% 초록불)**.
    - E2E 런타임 검증: 익명 포털(AI 제외 확인), 학생/교수 로그인 후 4대 앱 런처 및 모달 팝업/강좌 다이렉트 점프 100% 정상 작동.

* **학생 개인 성적 보호 및 학급 평균 성적 교수 전용 권한 격리 (2026-10-02)**:
  * **[사용자 요구사항]**:
    - "클래스룸에서 학급 평균 성적부분은 교수님만 볼수 있게 하자, 그리고 학생은 본인 성적확인만 가능한걸로 하자"
  * **[백엔드 보안 및 비즈니스 로직 격리]**:
    - `GradeBookDto`: `classAverageScore` 필드 타입을 기본형 `double`에서 래퍼 클래스 `Double`로 전환하여 비교수 사용자 조회 시 `null`을 명시적으로 반환할 수 있도록 개선.
    - `GradeService.getGradeBook`:
      - 학생(`isInstructor == false`) 요청 시 수강생 목록(`enrollments`)을 본인 1명으로 엄격 필터링(`filter(e -> e.getStudent().getId().equals(currentUserId))`).
      - 학급 전체 평균 성적(`classAverageScore`) 계산 및 할당을 교수(`isInstructor == true`) 조회 시에만 수행하고, 학생에게는 데이터베이스/API 응답 레벨에서 `null`로 완전히 차단.
  * **[Thymeleaf UI 화면 권한별 분기 (`detail.html`)]**:
    - **Tab 1 (수업 개요)**:
      - 교수: `학급 평균 성적` 통계 카드 노출 (`th:if="${isInstructor}"`).
      - 학생: `내 종합 성적 (점수 및 학점)` 카드 노출 (`th:unless="${isInstructor}"`).
    - **Tab 5 (성적부 일람표)**:
      - 교수: `수강생 수`, `학급 평균 성적` 통계 카드, 전체 학생 성적 매트릭스 그리드, `성적표 CSV 내보내기`.
      - 학생: `내 종합 점수 (학점)`, `과제 취득 점수`, `내 출석률` 3대 개인 요약 카드, 본인 성적 1행만 노출, `내 과제 포트폴리오 ZIP` 다운로드.
      - 타 학생 정보 및 학급 평균 점수 전면 은닉 및 `🔒 본인 성적만 안전하게 열람할 수 있도록 보호되며, 타 학생의 성적 및 학급 전체 평균 점수는 비공개 처리됩니다` 안내 배너 렌더링.
      - Thymeleaf 파서 레벨 주석(`<!--/* ... */-->`)을 적용하여 클라이언트 브라우저 페이지 소스 보기에서도 정보 유출 원천 차단.
  * **[테스트 및 E2E 무결성 검증]**:
    - `GradeServiceTest`:
      - `getGradeBook_student_onlyOwnGrade_and_noClassAverage` 단위 테스트 추가 (학생 조회 시 `classAverageScore == null`, `studentRows.size() == 1`, 본인 성적만 반환 검증).
    - Gradle 전체 빌드 및 테스트: **50/50 ALL PASS (100% 초록불)**.
    - E2E 런타임 검증 (`test_grade_privacy.py`):
      - 교수(`prof@uniclass.ac.kr`) 로그인: 개요/성적 탭 모두에서 학급 평균 성적 및 전 수강생 명단 정상 노출 확인.
      - 학생(`student@uniclass.ac.kr`) 로그인: 개요/성적 탭 모두에서 학급 평균 점수 및 타 학생 정보(`박컴공`) 100% 미노출 및 본인 성적만 정상 열람 확인.

#### 2. 다음 진행 계획 (Next Steps)
* **Phase 3: 교수 강의 준비 코파일럿 & 개인 자료 보관함 (Week 3 목표 착수 준비)**:
  * **Step 9 부가: 교수 개인 영구 자료 보관함 (My Library)**:
    - 수업과 독립된 교수 개인 드라이브/클라우드 보관함 구축.
  * **Step 10: AI 코파일럿 연동 (Google Gemini API)**:
    - 사용자 요청 시 착수 예정. 공휴일/시험이 반영된 유연한 주차 기반 16주차 강의계획서/커리큘럼 초안 자동 생성 및 퀴즈 출제.



