---
name: claude-audit
description: "Antigravity 내장 CLI를 통해 변경된 git diff를 Claude Sonnet 모델로 적대적 보안/NPE 정밀 리뷰 수행"
---

# Claude 적대적 코드 리뷰 스킬 (Claude Audit Skill)

이 스킬은 `project_one`의 변경 사항에 대해 클로드의 적대적 시각을 주입하여 결함을 차단하는 표준 실행 절차를 정의합니다.

## 실행 절차

1. **설계 의도 파악**:
   - 현재 변경된 코드의 목적과 핵심 의도를 한 줄 요약(`intent`)으로 작성합니다.

2. **리뷰 CLI 실행**:
   - 터미널에서 다음 명령어를 실행합니다.
   ```bash
   python scripts/claude_review_cli.py "<설계의도>"
   ```

3. **결과 해석 및 자가 수정**:
   - `ALL_CLEAR` 반환 시: 결함 없음으로 간주하고 최종 `./gradlew test` 검증으로 진행.
   - 결함 지적 시: 지적된 [보안 결함], [런타임 NPE], [엣지 케이스]를 반영하여 코드를 즉시 자가 수정(Self-Healing)하고 단위 테스트 보강.

4. **검증 종료**:
   - 수정 후 `./gradlew test`를 수행하여 모든 단위/통합 테스트가 통과하는지 확인.
