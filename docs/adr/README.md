# ADR

아키텍처에 중요한 영향을 주는 선택의 배경, 이유와 결과를 기록한다.

## 언제 쓰나

- 대상 판정과 다른 문서의 정본은 [governance.md](../contributing/governance.md)의 "가르는 기준"을 따른다.
- 한 ADR에는 한 중요한 결정을 짧게 기록한다. 독립적으로 채택하거나 대체할 결정은 나눈다.
- 기존 결정을 적용하는 구현 PR에서는 해당 ADR을 연결한다.

## 쓰는 법

1. [template.md](template.md)를 복사해 `NNN-short-english-title.md`로 만든다. 파일명의 설명은 영어 소문자와 하이픈을 쓴다.
   - NNN은 아래 목록과 [기존 RFC 목록](../rfcs/README.md)의 최대 번호 다음 세 자리 번호다. 번호를 재사용하지 않는다.
   - 아래 목록에 제목, 범위, 상태와 날짜를 추가한다.
   - 합의 전에는 `proposed`로 작성한다. 이미 합의한 결정을 기록하면 확정한 상태와 합의 날짜로 작성한다.
2. 배경, 결정과 이유, 결과를 작성한다.
   - 배경에는 당시 상황, 요구와 제약, 충돌하는 조건을 이해할 만큼 적는다.
   - 결정에는 선택한 것과 이유를 적고, 상세 구현은 필요한 코드와 문서에 연결한다.
   - 결과 절은 필수다. 실제 예상되는 영향과 감수할 제약을 적으며, 긍정적, 부정적, 중립적 영향으로 나눠 쓰는 것은 선택이다.
   - 검토한 선택지는 선택 항목이다. 실제 비교한 대안과 선택 이유가 있으면 적고, 리뷰에서 나온 유효한 반론도 반영한다.
3. PR을 연다. 제목은 `ADR-NNN: 제목`, 라벨은 `adr`다. [ADR PR 양식](../../.github/PULL_REQUEST_TEMPLATE/adr.md)을 따른다.
   - 웹에서는 아래 링크로 양식과 라벨을 선택한다.

   ```text
   https://github.com/asm17-ms2/meterengine/compare/main...<브랜치>?quick_pull=1&template=adr.md&labels=adr
   ```
4. [governance.md](../contributing/governance.md)의 "사전 합의" 조건에 따라 채택하거나 기각한다.
   - Discussion이나 회의에서 이미 동의받은 같은 결정을 기록하면 최종안, 동의 기록과 합의 날짜를 연결한다. 같은 결정을 다시 합의하지 않는다.
   - PR의 리뷰, 필수 승인과 검사는 [pull-request.md](../contributing/pull-request.md)를 따른다.
   - 머지 전에 작성자는 채택하면 `accepted`, 기각하면 `rejected`로 바꾸고 확정일을 문서와 아래 목록에 반영한다. 합의 전인 `proposed` 상태로 머지하지 않는다.
   - PR 체크리스트를 완료하고, 리뷰에서 합의 기록과 문서의 상태를 확인한다. 자동 검사는 체크 여부를 확인하며 문서 상태나 합의 여부를 대신 판단하지 않는다.
   - PR 승인이나 머지는 ADR 상태를 자동으로 바꾸지 않는다. 채택은 구현 완료를 뜻하지 않는다.
5. 결정을 바꾸면 새 ADR을 작성하고 아래 "대체"를 따른다.

## 기록 정보와 상태

| 필드 | 의미 |
| --- | --- |
| `status` | 아래 상태 중 하나 |
| `date` | 제안 중에는 작성일, 채택하거나 기각하면 그 결정을 확정한 날. 대체할 때는 기존 확정일을 보존 |
| `author` | 기록 작성자 |
| `domain` | 영향을 받는 범위. 백엔드 패키지에 한정하지 않고 여러 범위면 목록으로 작성. ADR 대상 판정의 문턱으로 쓰지 않음 |
| `supersedes`, `superseded-by` | 대체하는 기록과 대체한 기록의 상대 경로 목록. 해당하지 않는 필드는 생략 |

| 상태 | 의미 |
| --- | --- |
| `proposed` | 합의 전, 검토 중 |
| `accepted` | 채택한 결정. 구현 완료 여부는 작업과 PR에서 확인 |
| `rejected` | 채택하지 않기로 확정한 기록 |
| `superseded` | 이후 ADR로 대체된 결정 |

## 대체

- 채택하거나 기각한 문서의 당시 배경과 판단은 보존한다. 오탈자와 죽은 링크는 고칠 수 있다.
- 새 ADR의 `supersedes`에 대체할 ADR이나 RFC의 상대 경로를 적는다. 본문에서도 해당 기록과 바꾸는 결정을 연결한다.
- 새 ADR이 채택되면 이전 기록에 `status: superseded`와 `superseded-by`를 반영한다. 목록의 상태도 맞춘다.
  - 이전 기록의 결정 일부만 바꾸면 바꾸는 범위를 새 ADR에 명시하고, 전체를 대체하지 않은 이전 기록의 상태는 유지한다.
- 기존 RFC의 본문과 번호를 새 양식으로 일괄 다시 쓰지 않는다. 진행 중인 RFC는 문서별로 전환 여부를 확인한다.

## 목록

| 번호 | 제목 | 범위 | 상태 | 날짜 |
| --- | --- | --- | --- | --- |

## 참고

- [Michael Nygard, Documenting Architecture Decisions](https://www.cognitect.com/blog/2011/11/15/documenting-architecture-decisions).
- [AWS, Architectural decision record process](https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/adr-process.html).
