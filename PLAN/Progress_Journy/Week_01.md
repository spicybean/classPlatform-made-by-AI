# 📅 Progress Journey - Week 01

> **기간**: 2026-09-25 ~ 2026-10-01  
> **주요 목표**: 프로젝트 기획 확정, 요구사항 분석, 개발 환경 구성 및 Phase 1 핵심 MVP 착수

---

### 📝 2026-09-25 (금)

#### 1. 진행 내용 (What was done)
* **학교 현장 문제점 정의 및 요구사항 도출**:
  * 강의 자료 파편화(카톡, 이메일 등) 및 과제 제출/피드백 부재 문제 분석.
  * 구글 클래스룸의 한계를 극복하기 위한 교수자 지원(수업 복제, 4자리 출석, 코파일럿) 및 학생 중심 UX 기획.
* **학기 종료 및 데이터 라이프사이클 정책 수립**:
  * 데이터 하드 삭제 방지 및 수업 보관(Archive - Read Only) 정책 수립.
  * 학생 포트폴리오 다운로드 및 대용량 파일 관리 전략 마련.
* **기술 스택 확정 (Java / Spring Boot)**:
  * 언어: Java 25 (OpenJDK 25 환경 확인 완료)
  * 프레임워크: Spring Boot 3.x
  * 데이터베이스: Spring Data JPA + H2 (개발용) / PostgreSQL (운영용)
  * 화면(UI): Thymeleaf + Tailwind CSS (단일 스프링 모놀리식으로 빠른 빌드 및 토큰 절약 최적화)
* **문서화 및 계획 수립**:
  * 종합 프로젝트 기획서 작성 완료 (`PROJECT_PLAN.md`)
  * 마스터 TODO 리스트 작성 완료 (`PLAN/TODO.md`)
  * 개발 일지 시스템 구축 (`PLAN/Progress_Journy/Week_01.md`)

#### 2. 배운 점 / 주요 결정 사항 (Key Decisions)
* **학번/교번 필수 수집**: 학교 행정 및 출석/성적 일치를 위해 초기 프로필 수집 시 학번 입력 필수화.
* **파일 업로드 정책**: 스토리지 절약과 보안을 위해 파일당 20MB 제한 및 화이트리스트 확장자 검증 도입.
* **개발 효율성 극대화**: Spring Boot + Thymeleaf 조합으로 별도 프론트엔드 서버 구축 없이 단일 애플리케이션으로 경량화.

#### 3. 다음 진행 계획 (Next Steps)
* Spring Boot 3.x 기본 프로젝트 뼈대 생성 (Gradle Wrapper)
* 공통 웹 레이아웃(Tailwind CSS) 및 H2 데이터베이스 연동
* User 엔티티 및 회원가입/로그인 (Spring Security) 개발 착수

---

### 📝 2026-09-26 (토)

#### 1. 진행 내용 (What was done)
* **GitHub 원격 저장소 자동 생성 및 연동**:
  * 저장소명: `classPlatform-made-by-AI` (URL: `https://github.com/spicybean/classPlatform-made-by-AI`)
  * Java/Gradle 환경에 맞춘 `.gitignore` 및 공식 `README.md` 작성.
  * Git 초기화(`git init`), 첫 커밋(`Initial commit`), `main` 브랜치 원격 push 완료.
* **Git-flow 브랜치 전략 구성 완료**:
  * `main`을 기준으로 `develop`, `hotfix`, `release`, `test` 4개 브랜치 신규 생성.
  * `main`의 전체 내용을 `develop`으로 동기화 및 기본 작업 브랜치로 전환(`git checkout develop`).
  * 생성된 모든 브랜치를 GitHub 원격 저장소(`origin`)에 push 완료.
* **스프링 부트(Spring Boot) 핵심 뼈대 구축 완료**:
  * Spring Boot + Java 25 환경에 맞춘 Gradle Wrapper(`gradlew`, `gradlew.bat`, `gradle-9.7.1`) 연동.
  * `application.yml` 작성: H2 인메모리 DB 연동, 웹 콘솔(`/h2-console`) 활성화, 파일 업로드 용량 **20MB** 확장.
  * `SecurityConfig`: 초기 개발용 정적 리소스 및 H2 콘솔 접근 허용, BCryptPasswordEncoder 빈 등록.
  * `HomeController`: 메인 대시보드 뷰 컨트롤러 연동.
  * `Thymeleaf + Tailwind CSS`: 반응형 공통 레이아웃(`layout/default.html`) 및 홈 화면(`index.html`) 구현.
* **로컬 서버 스모크 테스트 (Smoke Test) 검증 통과**:
  * `./gradlew bootRun` 실행 ➡️ Tomcat 8080 포트 정상 기동.
  * 메인 홈(`http://localhost:8080/`) HTTP 200 OK 렌더링 확인.
  * H2 콘솔(`http://localhost:8080/h2-console`) 정상 접속 확인.

* **코드 리뷰 피드백 반영 완료**:
  * `application.yml`에 `uniclass.current-semester` 분리.
  * `HomeController` 필드 주입을 **생성자 주입**으로 리팩토링.
  * `layout/default.html` 상단바 학기 뱃지 동적 바인딩 및 인증 링크 적용.
* **사용자 인증 및 권한 (Auth & User) 기능 구현 완료**:
  * `Role`: `ROLE_STUDENT`, `ROLE_INSTRUCTOR`, `ROLE_TA`, `ROLE_ALUMNI` 4단계 역할 체계 정의.
  * `User`: JPA 엔티티 설계 (이메일, BCrypt 암호화 비밀번호, 실명, 학번/교번 필수 수집, 생성일시 자동 기록).
  * `UserRepository`: 이메일/학번 중복 검증 및 조회 인터페이스 구현.
  * `UserRegisterDto`: Bean Validation 적용 (이메일 포맷, 비밀번호 6자 이상, 학번 4~20자리 정규식 검증).
  * `CustomUserDetails` & `UserService`: 비밀번호 암호화 저장, `UserDetailsService` 연동으로 세션 로그인 처리.
  * `SecurityConfig`: 폼 로그인(`/login`), 로그아웃(`/logout`), 세션 무효화 및 쿠키 삭제, 접근 권한 체계화.
  * `AuthController`: 로그인/회원가입 뷰 렌더링 및 유효성 검증 에러 처리.
  * `login.html` & `register.html`: 반응형 모던 UI 폼 및 검증 에러/성공 메시지 피드백 렌더링.
  * `layout/default.html`: `sec:authorize`를 통한 로그인(사용자명, 학번, 역할 뱃지, 로그아웃 버튼) / 비로그인(로그인, 회원가입 버튼) 상태 분기 처리.

#### 2. 다음 진행 계획 (Next Steps)
* Phase 1 - 3단계: 수업 공간 및 수강 관리 (ClassRoom 엔티티, 6자리 초대 코드 생성, 수업 개설 및 수강 신청 뷰) 개발 착수

