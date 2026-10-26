# Oneclickme 기여 가이드

서버 개발자 2명이 이슈를 만들고, 코드를 리뷰하고, 병합할 때 사용하는 기준이다. 서버 리드는 재원이며, 리드가 작성한 PR에도 동일한 리뷰 규칙을 적용한다.

패키지 구조, Java·Spring, API, JPA·DB 규칙은 [개발 컨벤션](docs/conventions.md)에서 관리한다. 이 문서는 Git·이슈·PR 절차를 다룬다.

## 작업 흐름

GitHub Flow를 사용한다. 기준 브랜치는 `main`이며, 작업 브랜치에서 개발한 뒤 PR로 병합한다.

1. 작업에 맞는 이슈를 만들고 범위와 목적을 적는다.
2. 최신 `main`에서 `타입/이슈번호` 형식으로 브랜치를 만든다. 예: `feat/12`, `fix/23`.
3. 변경한 범위에 맞게 검증하고, 결과를 PR에 적는다.
4. `main`을 대상으로 PR을 만들고 다른 서버 개발자 1명에게 리뷰를 요청한다.
5. 상대방의 Approve를 받은 뒤 Squash merge한다. 병합한 작업 브랜치는 삭제를 권장한다.

`main` 병합 시 dev 서버에 자동 배포하는 것으로 정했다. 자동 배포와 CI 검증은 서버 리드가 추후 CI/CD를 구성할 때 적용한다.

## 커밋과 PR 제목

작업 브랜치의 커밋은 `타입: 작업 내용`으로 작성하며 이모지를 사용하지 않는다. PR 제목에는 태그를 붙이고 이모지를 사용하지 않으며, 최종 Squash 커밋에도 PR 제목을 유지한다.

| 타입 | 용도 | PR 제목 형식 |
|---|---|---|
| `feat` | 기능 추가 | `[Feat] 작업 내용` |
| `fix` | 버그 수정 | `[Fix] 작업 내용` |
| `chore` | 빌드, 의존성, 환경 설정 등 유지보수 | `[Chore] 작업 내용` |
| `docs` | 문서 작성·수정 | `[Docs] 작업 내용` |
| `refactor` | 기능 변경 없는 코드 구조 개선 | `[Refactor] 작업 내용` |

```text
브랜치:      feat/12
작업 커밋:   feat: 로그인 API 구현
PR 제목:     [Feat] 로그인 API 제작
Squash 제목: [Feat] 로그인 API 제작
```

GitHub가 Squash 제목에 붙이는 PR 번호는 유지할 수 있다. 최종 커밋 제목은 PR 제목의 태그 형식을 유지하며 작업 커밋 형식으로 바꾸지 않는다.

## 이슈 작성

[이슈 템플릿](.github/ISSUE_TEMPLATE)을 사용한다. Bug는 문제를 보고하는 이슈 유형이며, 수정 브랜치와 커밋에는 `fix`를 사용한다. Task는 여러 하위 이슈를 묶는 용도이며 별도의 커밋 타입이 아니다.

| 템플릿 | 용도 | 자동 지정 라벨 |
|---|---|---|
| Feature | 새로운 기능 | `:sparkles: Feature` |
| Bug | 재현 가능한 오류 | `:bug: Bug` |
| Refactor | 기능 변경 없는 구조 개선 | `♻️ Refactor` |
| Chore | 빌드·의존성·환경 설정 | `:broom: Chore` |
| Docs | 문서 작성·수정 | `:memo: Docs` |
| Task | 여러 하위 이슈를 묶는 상위 작업 | `:ghost: Task` |

자동 지정을 사용하려면 저장소에 위 이름의 라벨을 생성해야 한다. 템플릿 파일만으로 라벨이 생성되지는 않는다. [GitHub 이슈 폼 문서](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms)

## PR 작성과 리뷰

[PR 템플릿](.github/pull_request_template.md)에 작업 내용, 검증 방법과 결과, 리뷰 중점사항, 관련 이슈를 작성한다.

- 하나의 목적에 맞춰 PR을 구성한다. 변경량은 300~400줄 이내를 권장하며, 필요한 경우 목적이 끊기지 않는 범위로 조정한다.
- 실제 수행한 검증과 결과를 적는다. 검증하지 못했다면 이유를 적는다.
- 이슈를 완료하는 PR에는 `Closes #12`를, 단순 참조에는 `Refs #12`를 사용한다.
- 작성자 외 서버 개발자 1명의 Approve가 있어야 병합한다. 코멘트만 남긴 리뷰는 승인으로 보지 않는다.
- 승인 후 코드가 변경되어도 기존 승인을 자동으로 무효화하지 않는다.

## GitHub 설정

다음은 저장소에서 적용할 설정이다. 문서와 템플릿을 추가하는 것만으로 활성화되지는 않는다.

| 항목 | 설정 기준 |
|---|---|
| `main` 변경 | PR을 통해 병합 |
| 필수 리뷰 | 작성자 외 1명 승인, 리드도 동일하게 적용 |
| 승인 자동 무효화 | 끔 |
| 병합 방식 | Squash merge만 허용 |
| Squash 커밋 제목 | PR 제목 사용 |
| CODEOWNERS | 사용하지 않음 |
| CI 검증·dev 자동 배포 | 서버 리드가 추후 구성 |

PR 제목이 커밋 수에 관계없이 Squash 제목에 사용되도록 저장소의 기본 메시지 설정을 맞춘다. [GitHub Squash 설정](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/configuring-commit-squashing-for-pull-requests)

리뷰 필수 조건은 `main`의 브랜치 보호 또는 ruleset으로 적용한다. 관리자도 리뷰 없이 우회 병합하지 않도록 설정하며, 저장소의 공개 여부와 요금제에 따른 지원 범위는 설정 시 확인한다. [GitHub 브랜치 보호 문서](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
