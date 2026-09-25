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
* **뼈대 작업(Scaffolding) 범위 정의**:
  * Spring Boot 3.x + Gradle 빌드 설정
  * 패키지 구조(`domain/`, `global/`) 및 `application.yml` 환경 설정
  * Tailwind CSS 레이아웃 및 최초 스모크 테스트 계획 수립.

#### 2. 다음 진행 계획 (Next Steps)
* Spring Boot 3.x 기본 프로젝트 뼈대(Gradle 빌드 스크립트, Application 클래스, application.yml) 생성
* 로컬 서버 실행 검증 (Smoke Test) 및 GitHub 커밋/푸시

