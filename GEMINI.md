# 🚀 UniClass 개발 지침서 & 자동화 파이프라인 (GEMINI.md)

이 문서는 `project_one` (UniClass) 프로젝트에서 **Gemini Flash(구현/문서화) + Claude Sonnet(무인 정밀 리뷰)** 하이브리드 파이프라인을 운영하기 위한 프로젝트 표준 규칙입니다.

---

## 0. 프로젝트 환경 및 그라운딩 기준 (Project Context & Grounding)
- **동적 매니페스트 그라운딩 (Dynamic Manifest-driven Grounding)**:
  - AI는 특정 언어나 프레임워크 버전을 고정 상수로 가정하지 않고, 반드시 프로젝트 루트의 빌드 매니페스트(`build.gradle`, `pom.xml`, `package.json` 등)를 파싱하여 환경을 파악합니다.
  - 빌드 파일에 선언되지 않은 외부 의존성을 임의로 호출하거나, 명시된 버전에 맞지 않는 구형/폐기된 API 또는 미지원 문법을 사용하는 환각을 전면 금지합니다.
- **현재 프로젝트 스펙 (`build.gradle` 기준)**:
  - 언어 및 프레임워크: Java 25 (OpenJDK Temurin) / Spring Boot
  - 빌드 툴: Gradle 9.7.1 Wrapper (`./gradlew.bat`)
  - 화면(UI): Thymeleaf + Tailwind CSS CDN (Node.js 빌드 없음)
  - 데이터베이스: H2 인메모리 DB (`jdbc:h2:mem:uniclassdb`)
  - 보안/인증: Spring Security 세션 기반 인증
  - 검증 명령어: `.\gradlew.bat compileJava` 또는 `.\gradlew.bat test`
  - 작업 브랜치: `develop`

---

## 1. 100% 무인 4단계 하이브리드 파이프라인 (Phase 0 ~ Phase 4)

코드 수정이나 신규 기능 구현 시 다음 단계를 **사용자 개입 없이 시스템이 자동으로 완결**합니다:

```
[Phase 0: 사전 기획 및 매니페스트/규격 확인]
       │  (매니페스트 버전 파악, 기획/명세 확정. Java 임의 수정 금지)
       ▼
[Phase 1: 제미나이 코드 구현 (Antigravity Main)]
       │  (매니페스트 스펙을 100% 준수하여 Spring Boot 로직 및 실패 검증 테스트 작성)
       ▼
[Phase 2: 클로드 자동 적대적 감사 (Claude Audit Skill)] ★ 무인 트리거
       │  (규칙/Git Hook에 의해 사용자가 지시하지 않아도 백그라운드 자동 점화)
       │  (`scripts/claude_review_cli.py`가 치명적 결함 및 NPE 검출)
       ▼
[Phase 3: 제미나이 1회 자가 치유 (Self-Healing)]
       │  (Claude 피드백 반영 ➔ 무조건 1회로 상한선 강제하여 핑퐁 낭비 차단)
       ▼
[Phase 4: 결정론적 최종 검증 (Gradle)]
          (`.\gradlew.bat test` 통과 확인 후 Git 자동 커밋)
```

---

## 2. 무인 자동 리뷰 엔진 및 방어선 (`.agents/` & Git Hook)
- **무인 트리거 메커니즘 (Zero-Prompt)**:
  - **Rule (`.agents/rules/auto_review.md`)**: 매니페스트 그라운딩 불변식 및 비즈니스 로직 수정 완료 시 AI가 스스로 `claude-audit` 스킬을 호출하도록 시스템 프롬프트 레벨에서 강제.
  - **Hard Gate (`.git/hooks/pre-commit`)**: 커밋 시 1차로 Gradle 단위 테스트를 실행하여 실패 시 즉시 커밋을 물리적으로 차단(`exit 1`). 빠른 커밋 주기와 Claude API 쿼터 보호를 위해 Claude 감사는 온디맨드로 분리(`python scripts/claude_review_cli.py`).
- **프롬프트 인젝션 방어**: Diff 내용은 `<git_diff>` XML 태그로 격리.
- **단일 호출 상한선(Cap = 1회) 및 핵심 타겟팅**: 대용량 변경 시 다중 루프 분할을 금지하고, Security/Controller/Service 우선순위로 핵심 파일만 선별하여 단 1회만 호출함으로써 2시간 롤링 쿼터(429) 고갈 원천 차단.
- **선택적 헤더 주입**: 리뷰 대상 diff에 실제로 포함된 핵심 자바 파일의 선언부만 주입하여 컨텍스트 토큰 중복 낭비 차단.
- **설계 의도(Intent) 전달**: 실행 시 의도를 주입하여 오탐률 제어.
- **🚀 Fast-track (우회 조항 및 차단선)**: 단순 UI 오타 수정, 색상 변경, 주석 추가 등 비즈니스 로직 변화가 전혀 없는 경량 작업은 주간 쿼터 보호를 위해 Claude 리뷰를 생략 가능합니다. 단, 파일 변경이 1개라도 **Security 설정, 인증/인가 필터, 권한(RBAC), 결제/정산, DB 엔티티** 관련 수정인 경우 Fast-track 사용을 엄격히 금지합니다.
- **Gemini의 리뷰 번들 자동 조립 의무 (Manual Hand-off)**: Deep-Track 작업 검증이 끝나면 Gemini Flash는 커밋을 생성하기 전에 터미널에서 변경 사항을 조회하여, 사용자가 Claude Sonnet에 즉시 전달할 수 있는 아래 규격의 **[Claude 리뷰용 번들]** 코드블록을 반드시 최종 출력해야 합니다:
  ```text
  [리뷰 요청: Deep-Track]
  - 핵심 변경 파일: <파일 경로>
  - 클래스/트랜잭션 선언부:
    <클래스 어노테이션 및 선언부 10줄>
  - 변경 내역 (확장 Diff):
    <git diff -U10 내용>

  가이드라인에 따라 사설/칭찬 일체 배제하고 [보안], [동시성/경합], [엣지케이스] 위주로 엄격하게 리뷰하고 결함은 직접 디버깅해서 최종 코드를 제시해줘.
  ```

---

## 3. 코드 품질 & 백엔드 아키텍처 규칙
- **Spring Boot 백엔드 아키텍처 불변식 ([backend_architecture.md](file:///D:/Gemini/project_one/.agents/rules/backend_architecture.md))**:
  - `@Transactional(readOnly = true)` 기본 및 CUD 선택적 쓰기 트랜잭션.
  - 모든 JPA 연관관계 매핑 시 `fetch = FetchType.LAZY` 필수 (N+1 및 불필요 조인 차단).
  - 단건 조회 시 `findById().orElseThrow()`를 통한 Fail-Fast.
  - BOLA/IDOR 방어: 세션 사용자(`@AuthenticationPrincipal CustomUserDetails`) null 방어 및 리소스 소유권 일치 검증.
  - 엔티티 상태 캡슐화(`assertEditable()`) 및 DTO 계층 분리.
  - 보안 난수(인증코드 등) 생성 시 `java.security.SecureRandom` 필수.
- **Ponytail 린(Lean) 원칙**: 불필요한 과대 엔지니어링 금지, 표준 라이브러리 우선, 명확하고 단순한 코드 작성.
- **기록 유지 및 주차 증분 불변식 (Week Increment Rule)**:
  - **단일 증분 트리거 (Calendar-driven)**: 주차 번호(`Week_{XX}`)는 Phase/마일스톤과 무관하게 **오직 캘린더 주(매주 월요일 00:00 KST)**가 도래할 때만 직전 번호에 +1을 더한 2자리 포맷(`Week_01`, `Week_02`, `Week_03` ...)으로 새 파일을 개설합니다. (Phase 및 마일스톤 정보는 파일 상단 메타데이터 헤더에 명시).
  - **동기화 주기 (Session/Feature-based)**: 모든 마이크로 커밋마다 문서를 매번 수정하지 않으며, **주요 기능 단위 개발 완료 시점 또는 일일 세션 종료 시점**에 1회 일괄 최신화하여 불필요한 Git diff 및 문서 유지보수 피로도를 차단합니다.
  - **3대 문서 역할 분리 및 세트 동기화**:
    1. `PLAN/Progress_Journy/Week_{XX}.md`: 일자별 상세 구현 내역, 아키텍처 결정, 커밋 해시 기록 (구현 팩트 중심).
    2. `PLAN/CodeReview/Week_{XX}.md`: 보안(BOLA/IDOR), DTO 유효성, 자가 치유(Self-Healing), 테스트 통과 내역 (품질/보안 감사 중심).
    3. `PLAN/TODO.md`: 매크로 진척도 체크박스(`[x]`) 상태만 추적 (상세 커밋/구현 내역 중복 기재 금지).


---

## 4. 엄격한 테스트 및 조작 방지 규칙 (Anti-Fake-Test) ★

AI는 "무조건 초록불(Pass)"을 만들기 위한 어떠한 치팅이나 조작도 부려선 안 되며 다음 기준을 엄격히 준수합니다:

1. **시나리오와 코드 구현의 분업 (토큰 최적화)**:
   - 클로드(Claude)는 절대 Java 테스트 코드를 길게 작성하지 않으며, 오직 **'가혹한 실패/엣지 케이스 시나리오 3~5개'를 한글 불릿 포인트로만 명세**합니다 (토큰 소모 500개 미만 제한).
   - 실제 `@Test` Java 코드를 작성하고 통과시키는 일은 **제미나이(Gemini Flash)**가 전담합니다.
2. **실패 케이스(Negative Test) 필수**:
   - 성공 케이스만 작성하는 해피 패스(Happy Path) 테스트를 금지합니다.
   - 잘못된 입력값, 중복 데이터, 권한 없는 접근에 대해 정확한 예외(`assertThrows`)가 터지는지 검증하는 테스트를 무조건 1개 이상 포함하십시오.
3. **조건부 테스트 수정 허용 (Test Freezing)**:
   - 빌드 실패를 모면하기 위해 기존 테스트 기댓값을 몰래 수정하는 행위를 엄격히 금지합니다.
   - 단, **사용자가 명시적으로 비즈니스 정책이나 요구사항의 변경을 지시한 경우**에 한하여 예외적으로 테스트 코드 수정을 허용합니다.
4. **자문자답 모킹(Fake Mocking) 및 예외 은폐 금지**:
   - 테스트 대상이 되는 핵심 비즈니스 로직 자체를 `Mockito`로 하드코딩 리턴하여 껍데기만 통과시키는 행위를 금지합니다.
   - 테스트 코드 내부에서 `try-catch`로 예외를 삼키고 무마하는 행위를 금지합니다.
5. **구체적 단언문 강제**:
   - `assertTrue(true)`나 단순 `assertNotNull()` 단독 사용을 금지하며, `assertThat(actual).isEqualTo(expected)`로 실제 필드 값을 정밀 검증하십시오.

---

## 5. 외부 도구(MCP & Skills) 제한적 운용 전략

도구 남용으로 인한 시스템 병목과 프레임워크 충돌을 방지하기 위해, 외부 확장 도구는 파이프라인에 상시 종속시키지 않고 다음 원칙에 따라 분리 운용합니다.

1. **Chrome DevTools MCP (동적 E2E 검증)**
   - **적용 범위**: 상시 적용 불가. 비즈니스 치명도가 가장 높은 핵심 플로우(회원가입/로그인, 수업 초대 코드 렌더링)에 한정하여 제한적으로 투입.
   - **목적**: 백엔드 정적 분석이 잡아내지 못하는 프론트엔드(Thymeleaf) 렌더링 에러 및 브라우저 콘솔 에러 검출.
2. **Notion MCP (문서 동기화)**
   - **적용 범위**: 커밋 단위의 실시간 동기화 금지 (API 병목 및 포맷 유실 방지).
   - **목적**: 리뷰 및 개발 기록은 속도가 빠른 로컬 마크다운(`.md`) 파일에 누적하고, 1주일 단위의 마일스톤(Sprint) 종료 시점에만 요약본을 Notion 워크스페이스로 일괄(Batch) 푸시.
3. **Ponytail Skill (코드 다이어트 정기 감사)**
   - **적용 범위**: 자동화 파이프라인 삽입 금지 (Spring Boot 표준 패턴 오진단 방지).
   - **목적**: 특정 기능 개발 챕터가 완료된 시점에 인간 개발자가 수동으로 트리거(`ponytail-audit`)하여, 누적된 과대 엔지니어링과 불필요한 레거시 코드를 제거하는 주간 정기 검진 용도로만 활용.
4. **Backend Auditor Skill (적대적 백엔드/보안 감사)**
   - **적용 범위**: 온디맨드 (`backend-auditor` 스킬 수동 호출 또는 핵심 API 수정 시 선택적 트리거).
   - **목적**: BOLA/IDOR 취약점, JPA N+1 쿼리 누수, 트랜잭션 정합성 결함, DTO 오염을 공격자 시각에서 점검하고 재현 시나리오 및 패치 도출.

---

## 6. OpenAI Codex CLI 활용 및 비상 감사 수칙 (Phase 0 & Failover)
1. **Phase 0: 사전 규격 전담 (Spec-only)**:
   - 신규 기능 개발 시작 전, ChatGPT 앱에서 토론한 결과를 바탕으로 오직 **"DB ERD, DTO 규격, API 계약"만 `.ai/specs/<기능>.md`로 생성**하는 용도로만 Codex CLI를 호출합니다.
   - **절대 금지**: Codex CLI에게 `src/main/java/` 내 자바 소스코드를 직접 작성/수정하게 하는 행위를 전면 금지합니다 (Gemini 작업과의 충돌 및 쿼터 고갈 방지).
2. **Claude 방전 시 비상 감사관 (Emergency Failover)**:
   - Antigravity 내부 Claude 쿼터가 0%로 고갈(`429` 또는 에러)될 경우, `scripts/claude_review_cli.py`가 자동으로 터미널의 `codex` CLI로 비상 전환(Failover)하여 3줄 불릿 포인트 감사를 이어받습니다.
3. **1일 1~2회 쿼터 상한선(Cap)**:
   - Codex CLI는 일상적인 마이크로 커밋에 무차별 연동하지 않으며, 하루 최대 1~2회로 호출 횟수를 엄격히 제한하여 무료 쿼터를 보존합니다.

---

## 7. AI 응답 톤앤매너 및 의례적 표현 전면 금지 규정 (Non-Sycophancy Rule) ★

앞으로의 모든 질의응답 및 리뷰 시 AI는 다음 원칙을 절대적으로 준수해야 합니다:

1. **의례적인 칭찬 및 동조 표현 일체 금지**:
   - 금지 표현 예시: "완벽합니다", "정확한 지적입니다", "날카롭네요", "좋은 질문입니다", "전적으로 동의합니다" 등.
   - 영혼 없는 립서비스나 아첨(Sycophancy)성 서론을 작성하지 마십시오.
2. **직답 및 근거 우선 원칙**:
   - 질문이나 의견을 받으면 서론 없이 **즉시 결론에 바로 답하고, 필요한 정보와 객관적 근거를 최우선으로 제시**하십시오.
3. **조건부 평가/칭찬 원칙**:
   - 칭찬이나 긍정 평가는 사용자가 명시적으로 평가를 요구했거나, 실제로 객관적 평가가 필요한 경우에 한하여 **구체적인 기술적 이유와 함께 제공**하십시오.
4. **비판적 동의(타당성 vs 불확실성 분리)**:
   - 사용자의 의견에 동의할 때도 무비판적으로 맞장구치지 마십시오.
   - 반드시 **[타당한 점]과 [틀리거나 불확실한 점 / 잠재적 위험]을 분리하여 설명**하십시오.
5. **디버깅 시 억지 반론 금지 및 근거 기반 직답**:
   - 아키텍처 설계·기술 도입 질문에는 정·반·합(Thesis-Antithesis-Synthesis) 검증 구조를 적용하되, 명확한 버그/에러 디버깅 및 단순 사실 확인 질문에는 억지 반론(Forced Antithesis)을 생성하지 마십시오.
   - 디버깅 시에는 **[에러 로그/스택 트레이스 근거 ➔ 정확한 근본 원인(Root Cause) ➔ 최소한의 교정 패치]** 순서로만 답하고, 확인되지 않은 런타임 상태는 테스트/로그 실행을 통해 먼저 검증하십시오.

---

## 8. 3-Tier 권한 모델 및 파괴적 작업 사전 승인 (Tier 3 Hard-Stop Gate) ★

- **Tier 1 (무조건 자동 통과)**: 로컬 파일 읽기, 빌드(`compileJava`), 로컬 단위 테스트(`gradlew test`), linter 검사, 비즈니스 코드 구현 및 1회 자가 치유. 승인 팝업 없이 백그라운드에서 신속 무인 실행.
- **Tier 2 (로컬 격리 자동화)**: 로컬 Git 커밋(`git commit`), 로컬 패키지 설치. 언제든 `git reset`으로 복구 가능하므로 자동 실행.
- **Tier 3 (절대적 인간 승인 강제)**:
  1. 원격 푸시 및 강제 이력 삭제 (`git push`, `git push --force`, `git reset --hard`)
  2. DB 테이블/데이터 영구 삭제 (`DROP TABLE`, `TRUNCATE TABLE`, 무조건절 `DELETE`)
  3. 디렉토리/파일 영구 삭제 (`rm -rf`, `Remove-Item -Recurse`)
  4. `.env` 및 시크릿 키 조작 또는 외부 전송
  5. 외부 프로덕션 배포 및 패키지 퍼블리시
  - **행동 강제**: 위 Tier 3 작업이 발생할 경우 AI는 명령어를 절대 임의 실행하지 못하며, 반드시 **[대상 리소스], [실행 명령어], [파괴적 위험]**을 요약 보고하고 사용자의 명시적 동의를 구해야 합니다.


