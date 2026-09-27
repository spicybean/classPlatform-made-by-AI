# 🚀 UniClass 개발 지침서 & 자동화 파이프라인 (GEMINI.md)

이 문서는 `project_one` (UniClass) 프로젝트에서 **Gemini Flash(구현/문서화) + Claude Sonnet(무인 정밀 리뷰)** 하이브리드 파이프라인을 운영하기 위한 프로젝트 표준 규칙입니다.

---

## 0. 프로젝트 환경 (Project Context)
- **언어 및 프레임워크**: Java 25 (OpenJDK Temurin) / Spring Boot
- **빌드 툴**: Gradle 9.7.1 Wrapper (`./gradlew.bat`)
- **화면(UI)**: Thymeleaf + Tailwind CSS CDN (Node.js 빌드 없음)
- **데이터베이스**: H2 인메모리 DB (`jdbc:h2:mem:uniclassdb`)
- **보안/인증**: Spring Security 세션 기반 인증
- **검증 명령어**: `.\gradlew.bat compileJava` 또는 `.\gradlew.bat test`
- **작업 브랜치**: `develop`

---

## 1. 100% 무인 듀얼 모델 자동화 파이프라인 (Zero-Touch Pipeline)

코드 수정이나 신규 기능 구현 시 다음 5단계를 **사용자 개입 없이 AI가 자동으로 완결**합니다:

```
[1. 제미나이 코드 작성]
       │
       ▼
[2. 클로드 자동 호출 (`python scripts/claude_review_cli.py`)]
       │  (Antigravity 내장 CLI로 git diff 전송 ➔ 3~5줄 초경량 핵심 결함 수신)
       ▼
[3. 제미나이 정밀 문서화]
       │  (클로드 피드백을 바탕으로 `PLAN/CodeReview/Week_XX.md`에 상세 보고서 작성)
       ▼
[4. 제미나이 자가 수정 (Self-Healing)]
       │  (지적된 보안/NPE/엣지 케이스 버그를 코드에 직접 반영)
       ▼
[5. 최종 빌드 검증 및 완료 보고]
          (`.\gradlew.bat compileJava` 통과 확인 후 사용자에게 최종 완료 요약 보고)
```

---

## 2. 자동 리뷰 엔진 안전 규칙 (`scripts/claude_review_cli.py`)
- **프롬프트 인젝션 방어**: Diff 내용은 `<git_diff>` XML 태그로 격리하여 코드 내 조작 문자열 차단.
- **토큰 폭탄 방지**: Diff 길이를 최대 8,000자로 슬라이싱하여 1회 호출당 토큰을 약 2,000개 이내로 엄격 통제.
- **클로드 지침**: 사설과 문서 생성을 일체 금지하고 [보안], [런타임 에러/NPE], [엣지 케이스] 3~5줄 불릿 포인트만 출력하도록 강제.

---

## 3. 코드 품질 & 프로젝트 관리 규칙
- **Ponytail 린(Lean) 원칙**: 불필요한 과대 엔지니어링 금지, 표준 라이브러리 우선, 명확하고 단순한 코드 작성.
- **방어적 코딩**: `@AuthenticationPrincipal CustomUserDetails` 사용 시 비인증 null 방어 및 유효성 검증 필수.
- **기록 유지**: 작업 완료 시 `PLAN/Progress_Journy/Week_XX.md` 및 `PLAN/TODO.md`에 완료 상태를 동기화.
