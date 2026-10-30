# UniClass 프로젝트 개발 지침서 (GEMINI.md)

이 문서는 `project_one` (UniClass) 고유의 기술 스펙, 감사 파이프라인, 문서화 규칙을 정의합니다. 범용 보안·추론·출력 규율 및 파괴적 작업 사전 승인 게이트는 전역 불변 규칙(`user_global`)을 따릅니다.

---

## 1. 프로젝트 환경 스펙 (`build.gradle` 기준)
- **언어 및 프레임워크**: Java 25 (OpenJDK Temurin) / Spring Boot
- **빌드 및 검증**: Gradle 9.7.1 Wrapper (`.\gradlew.bat compileJava`, `.\gradlew.bat test`)
- **화면(UI)**: Thymeleaf + Tailwind CSS CDN (별도 Node.js 빌드 없음)
- **데이터베이스**: H2 인메모리 DB (`jdbc:h2:mem:uniclassdb`)
- **보안/인증**: Spring Security 세션 기반 인증
- **작업 브랜치**: `develop`

---

## 2. 계층형 감사 및 검증 파이프라인
- **1차 구조 감사 (`code-auditor` 서브에이전트)**:
  - 인증/인가, 외부 입력 바인딩, DB 상태 변이(CUD), 조인 쿼리 변경 시 `invoke_subagent(TypeName="code-auditor")`에 변경된 Git Diff와 파일 경로를 전달해 감사를 수행합니다 (단순 조회·문구·비-자바 정적 리소스 수정은 생략).
- **2차 교차 감사 (`claude-audit` CLI — 고위험/마일스톤 검증)**:
  - Security 설정, 인가 필터, 다중 트랜잭션 경합 등 고위험 로직 변경 또는 주요 기능 완료 시 `python scripts/claude_review_cli.py "<설계의도>"`를 단 1회(`Cap = 1회`) 호출하여 이기종 모델 교차 검증을 수행합니다.
  - **쿼터 고갈(`429` / `RESOURCE_EXHAUSTED`) Fallback**:
    1. 터미널의 `codex` CLI로 비상 전환하여 감사를 이어받습니다 (하루 최대 1~2회 제한, `src/main/java/` 코드 직접 수정 금지).
    2. 또는 사용자가 외부에서 검토할 수 있도록 핵심 파일 선언부(10줄)와 `git diff -U10`을 묶은 `[Claude 리뷰용 번들]` 코드블록을 출력합니다.
- **자가 치유 상한 및 커밋 게이트**:
  - 감사에서 도출된 결함은 최대 1회(Max Loop = 1) 자가 수정하며, 해결되지 않을 경우 작업을 멈추고 사용자에게 보고합니다.
  - 로컬 커밋 시 `.git/hooks/pre-commit`이 `.\gradlew.bat test`를 실행하여 테스트 실패 시 커밋을 차단(`exit 1`)합니다.

---

## 3. 백엔드 아키텍처 및 문서 동기화 규칙
- **Spring Boot 백엔드 불변식 ([backend_architecture.md](file:///D:/Gemini/project_one/.agents/rules/backend_architecture.md))**:
  - Service 클래스 기본 `@Transactional(readOnly = true)` 선언 및 CUD 메서드 선택적 `@Transactional` 부여.
  - 모든 JPA 연관관계 `fetch = FetchType.LAZY` 명시.
  - 단건 조회 시 `findById().orElseThrow()` Fail-Fast 처리.
  - `@AuthenticationPrincipal CustomUserDetails` null 방어 및 리소스 소유권(BOLA/IDOR) 일치 검증.
  - 엔티티 상태 캡슐화(`assertEditable()`) 및 Controller-Service 간 DTO 계층 분리.
  - 인증코드·초대코드 생성 시 `java.security.SecureRandom` 사용.
- **기록 유지 및 주차 증분 규칙 (Week Increment Rule)**:
  - **활성 주차 연속 증분**: 주차 번호(`Week_{XX}`)는 실제 개발이 진행된 '활성 주차'를 기준으로 직전 번호에 +1을 더한 2자리 포맷(`Week_01`, `Week_02`, `Week_03`, `Week_04` ...)으로 연속 개설합니다. (공백 캘린더 주는 건너뛰며, 파일 상단 헤더에 실제 작업 기간 `YYYY-MM-DD ~ YYYY-MM-DD` 및 Phase 명시).
  - **동기화 주기**: 마이크로 커밋마다 수정하지 않고, 주요 기능 단위 완료 또는 일일 세션 종료 시점에 1회 일괄 최신화합니다.
  - **3대 문서 역할 분리**:
    1. `PLAN/Progress_Journy/Week_{XX}.md`: 일자별 구현 내역, 아키텍처 결정, 커밋 해시 기록.
    2. `PLAN/CodeReview/Week_{XX}.md`: 보안(BOLA/IDOR), DTO 검증, 자가 치유, 테스트 결과 기록.
    3. `PLAN/TODO.md`: 매크로 진척도 체크박스(`[x]`) 상태만 추적.

---

## 4. 프로젝트 테스트 작성 기준 (Anti-Fake-Test)
1. **실패 케이스(Negative Test) 포함**: 잘못된 입력값, 중복 데이터, 권한 없는 접근에 대해 예외(`assertThrows`) 발생 여부를 검증하는 테스트를 최소 1개 이상 포함합니다.
2. **테스트 임의 수정 금지**: 요구사항·비즈니스 정책 변경이 명시된 경우를 제외하고, 빌드 통과를 위해 기존 테스트 기댓값을 임의 수정하지 않습니다.
3. **핵심 로직 모킹 금지**: 검증 대상이 되는 비즈니스 로직 자체를 `Mockito`로 하드코딩 리턴하거나 테스트 내부에서 `try-catch`로 예외를 삼키지 않습니다.
4. **구체적 단언문 사용**: 단순 `assertNotNull()` 단독 사용 대신 `assertThat(actual).isEqualTo(expected)`로 실제 필드 값을 검증합니다.

---

## 5. 외부 확장 도구(MCP & Skills) 운용 기준
- **Chrome DevTools MCP**: 핵심 사용자 플로우(회원가입/로그인, 초대 코드, 대시보드 렌더링)의 프론트엔드/콘솔 에러 검증 시 투입.
- **Notion MCP**: 커밋 단위 실시간 호출을 피하고, 마일스톤(Sprint) 종료 시점에 로컬 마크다운 요약본을 일괄(Batch) 동기화.
- **Ponytail Skill**: 자동 파이프라인에서 제외하며, 기능 챕터 완료 후 수동 트리거(`ponytail-audit`)로 불필요한 레거시·과대 엔지니어링 정리 시 활용.



