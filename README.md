# 🎓 classPlatform-made-by-AI (UniClass)

> **차세대 학교/대학용 스마트 학습 관리 플랫폼 (LMS)**  
> 구글 클래스룸의 한계를 뛰어넘어 교수자의 강의 준비 부담을 덜고 학생들의 학습 참여를 극대화하는 플랫폼입니다.

---

## 🛠️ 기술 스택 (Tech Stack)
* **언어**: Java 25 (LTS)
* **프레임워크**: Spring Boot 3.x
* **데이터베이스**: Spring Data JPA (Hibernate) + H2 (개발용) / PostgreSQL (운영용)
* **보안**: Spring Security (세션 기반 인증)
* **화면(UI)**: Thymeleaf + Tailwind CSS

---

## 📁 주요 문서 링크
* [전체 기획서 (PRD)](./PROJECT_PLAN.md)
* [프로젝트 마스터 TODO 리스트](./PLAN/TODO.md)
* [주차별 개발 일지 (Progress Journey)](./PLAN/Progress_Journy/Week_01.md)

---

## 🌟 핵심 기능
1. **역할 기반 권한**: 교수(Instructor), 조교(TA), 학생(Student), 수료생(Alumni)
2. **수업 개설 및 6자리 코드 참여**: 원클릭 수업 개설 및 수강 신청
3. **주차별 강의 자료실**: 1~16주차 자료 업로드, 브라우저 전체화면 강의실 모드
4. **과제 및 스피드 채점기 (Speed Grader)**: 웹 뷰어 즉시 채점, 루브릭 기준표, 지각 자동 판별
5. **실시간 4자리 간편 출석**: 3분 유효 랜덤 번호로 모바일/PC 즉시 출석 체크
6. **강의 준비 코파일럿**: 작년 동학기 수업 원클릭 복제 (일정 자동 시프트), AI 커리큘럼 보조
7. **학기 종료 아카이빙**: 데이터 유실 없는 읽기 전용 보관 및 학생 포트폴리오 ZIP 다운로드
