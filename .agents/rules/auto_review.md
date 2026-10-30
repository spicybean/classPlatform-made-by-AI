---
description: "계층형 코드 감사(1차 code-auditor 서브에이전트 + 2차 claude-audit CLI) 및 자가 치유 규칙"
---

# 계층형 코드 감사 및 자가 치유 규칙 (Hierarchical Review Rule)

이 규칙은 `project_one` 내에서 Java 소스코드를 수정하는 모든 작업에 적용됩니다.

## 1. 사전 명세(Spec) 및 매니페스트 준수
- 소스코드 작성 전 프로젝트 루트 매니페스트(`build.gradle`)의 의존성/버전과 `PLAN/` 문서에 정의된 도메인 모델, DTO, API 요구사항을 준수합니다.

## 2. 계층형 감사 파이프라인
1. **1차 구조 감사 (`code-auditor` 서브에이전트)**:
   - 인증/인가, 외부 입력 바인딩, DB 상태 변이(CUD), 복잡한 조인 쿼리 변경 완료 시 `invoke_subagent(TypeName="code-auditor")`에 Git Diff와 파일 경로만 전달해 감사를 수행합니다.
   - 단순 조회(Read-only), 오타·문구 수정, 비-자바 정적 리소스(`.html`, `.css`, `.js`, `.md`, `.yml`) 변경 시에는 감사를 생략하고 즉시 테스트 및 커밋으로 직행합니다.
2. **2차 교차 감사 (`claude-audit` CLI — 고위험/마일스톤 검증)**:
   - Spring Security 설정, 인가 필터, 다중 트랜잭션 경합 등 고위험 코어 로직 변경 또는 주요 기능 완료 시 `python scripts/claude_review_cli.py "<설계의도>"`를 단 1회(`Cap = 1회`) 호출하여 이기종 모델 교차 검증을 수행합니다.
   - **쿼터 고갈(`429` / `RESOURCE_EXHAUSTED`) Fallback**:
     1. 터미널의 `codex` CLI로 비상 전환하여 감사를 이어받습니다 (하루 최대 1~2회 제한, `src/main/java/` 코드 직접 수정 금지).
     2. 또는 사용자가 외부에서 검토할 수 있도록 핵심 파일 선언부(10줄)와 `git diff -U10`을 묶은 `[Claude 리뷰용 번들]` 코드블록을 화면에 출력합니다.

## 3. 자가 치유 상한선 (Max Loop = 1)
- 감사에서 도출된 결함은 최대 1회 자가 수정(Self-Healing)하며, 1회 수정 후에도 결함이 지속되거나 테스트가 깨지면 무한 루프를 돌지 말고 작업을 중단하고 사용자에게 보고합니다.

## 4. 최종 커밋 게이트
- `.git/hooks/pre-commit`을 통해 `.\gradlew.bat test`를 실행하여 실패 시 커밋을 차단(`exit 1`)합니다.
