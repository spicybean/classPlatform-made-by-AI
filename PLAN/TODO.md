# 📋 UniClass 프로젝트 마스터 TODO 리스트

> **기술 스택**: Java 25 / Spring Boot 3.x / Spring Data JPA / H2 & PostgreSQL / Thymeleaf + Tailwind CSS  
> **최종 수정일**: 2026-09-25

---

## 📌 Phase 1: 핵심 MVP 개발 (1주차 목표)

### 1. 프로젝트 초기 환경 구축
- [x] Spring Boot 3.x 프로젝트 뼈대 생성 (Gradle Wrapper, Java 25)
- [x] 필수 의존성 설정 (`build.gradle`: Spring Web, Thymeleaf, Spring Data JPA, H2, Spring Security, Validation)
- [x] Tailwind CSS 연동 및 공통 레이아웃 템플릿 작성 (`layout/default.html`, `index.html`)
- [x] H2 Console 및 DB 연결 테스트 (웹 콘솔 정상 구동 확인)
- [x] 로컬 서버 스모크 테스트 통과 (HTTP 200 OK)

### 2. 사용자 인증 및 권한 (Auth & User)
- [x] `User` 엔티티 설계 (이메일, 암호화 비밀번호, 실명, 학번/교번, 역할)
- [x] Spring Security 세션 로그인 / 회원가입 구현 (`/login`, `/register`)
- [x] 역할별 인가 설정 (`ROLE_INSTRUCTOR`, `ROLE_STUDENT`, `ROLE_TA`, `ROLE_ALUMNI`)
- [x] 회원가입 시 학번/교번 유효성 검증 (정규식 및 중복 방지)

### 3. 수업 공간 및 수강 관리 (Classroom & Enrollment)
- [ ] `ClassRoom` 엔티티 및 `Enrollment` (수강생 매핑) 엔티티 설계
- [ ] 교수용: 새 수업 개설 폼 (수업명, 학기, 분반 등)
- [ ] 6자리 랜덤 초대 코드 생성 로직 (중복 방지)
- [ ] 학생용: 초대 코드 입력 및 수강 신청 기능
- [ ] 교수/학생 대시보드 (내가 참여 중인 수업 목록 뷰)

### 4. 주차별 강의 자료실 (Materials)
- [ ] `WeekSection`(주차) 및 `Material`(강의 자료) 엔티티 설계
- [ ] 로컬 파일 스토리지 업로드 서비스 구현 (용량 20MB 제한, 화이트리스트 확장자 검증)
- [ ] 주차별(1~16주차) 자료 등록 및 학생 파일 다운로드 기능
- [ ] 예약 발행(Scheduled Release) 플래그 및 공개 시점 필터링 로직

### 5. 과제 출제 및 제출 시스템 (Assignments & Submissions)
- [ ] `Assignment`(과제) 및 `Submission`(제출물) 엔티티 설계
- [ ] 교수용: 과제 출제 폼 (마감일시, 배점, 지각 허용 여부, 첨부파일)
- [ ] 학생용: 과제 파일 제출 인터페이스 (정상 제출 / 지각 제출 상태 자동 판별)
- [ ] 학생용: 마감 전 과제 수정/재제출 로직
- [ ] 교수용: 학생별 제출 현황 일람표 및 제출 파일 다운로드

---

## 📌 Phase 2: 수업 운영 편의성 강화

### 6. 스피드 그레이더 (Speed Grader)
- [ ] 웹 브라우저 내 PDF/이미지/텍스트 과제 즉시 뷰어
- [ ] 루브릭(채점 기준표) 체크박스 및 점수 자동 합산
- [ ] 개별 학생 맞춤형 피드백 작성 및 알림

### 7. 실시간 4자리 간편 출석 체크 (Attendance)
- [ ] `AttendanceSession`, `AttendanceRecord` 엔티티 설계
- [ ] 교수용: 3분 유효 4자리 랜덤 숫자 팝업 생성
- [ ] 학생용: 모바일/PC 간편 번호 입력 폼 및 출석/지각 처리
- [ ] 실시간 출석 현황 통계 대시보드

### 8. 학기 종료 및 데이터 보존 (Lifecycle & Archiving)
- [ ] 수업 보관(Archive) 기능 (`is_archived = true`, 읽기 전용 전환)
- [ ] 학생용: 종강/졸업 시 내 과제물 및 피드백 일괄 ZIP 다운로드
- [ ] 교수용: 학기 성적표 및 제출물 일괄 엑셀/ZIP 내보내기

---

## 📌 Phase 3: 교수 강의 준비 코파일럿 & 부가 기능

### 9. 수업 복제 및 개인 보관함
- [ ] 작년 동학기(1년 전) 수업 원클릭 복제 기능
- [ ] 개강일 기준 주차별 날짜 및 과제 마감일 자동 시프트 (Date Shift)
- [ ] 교수 개인 영구 자료 보관함 (My Library)

### 10. AI 코파일럿 연동
- [ ] Google Gemini API 연동 모듈
- [ ] 과목명 기반 16주차 강의계획서/커리큘럼 초안 자동 생성
- [ ] 슬라이드(PPT/PDF) 기반 확인 퀴즈 자동 출제
