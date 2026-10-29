# Oneclickme 서버 개발 컨벤션

서버 개발자 2명이 함께 적용할 패키지, 코드, API, JPA·DB 기준을 정리한다. 환경 구성과 자동화는 아래에 명시한 구현 상태를 구분해 읽는다.

Git·이슈·PR 절차는 [기여 가이드](../CONTRIBUTING.md)에서 관리한다.

## 패키지 구조

`com.hsu.oneclickmeet` 아래에 `domain`과 `global`을 둔다. `domain`은 업무별 기능을 묶는 패키지이며 `member`, `place` 등을 하위에 둔다.

```text
com.hsu.oneclickmeet
├── OneclickmeApplication
├── domain
│   ├── member
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   │       ├── request
│   │       └── response
│   └── place
│       └── ...
└── global
    ├── config
    ├── exception
    └── entity
```

위 구조는 배치 기준이다. 하위 패키지는 실제 코드가 필요할 때 만든다.

- 특정 업무의 엔티티·규칙·오류 정의는 해당 도메인에 둔다.
- 여러 도메인에서 사용하더라도 해당 업무가 소유하는 코드는 그 도메인에 둔다.
- 공통 예외 처리와 오류 응답 변환은 `global.exception`에 둔다.
- 공통 설정은 `global.config`, 생성·수정 시각을 공유하는 `BaseTimeEntity`는 `global.entity`에 둔다.
- `common`에는 목적이 불분명한 코드를 모으지 않는다. 공통 역할이 확인되면 목적에 맞는 패키지를 만든다.

## Java와 Spring

| 항목 | 기준 |
|---|---|
| 클래스 이름 | `PascalCase`: `PlaceController`, `PlaceService`, `PlaceRepository` |
| 메서드·변수 이름 | `camelCase`: `createPlace`, `changePlaceName`, `placeId` |
| 상수 이름 | `UPPER_SNAKE_CASE` |
| 엔티티 이름 | 단수 명사: `Place`, `Member` |
| 요청 DTO | `dto.request`에 `CreatePlaceRequest`처럼 용도가 드러나는 이름 사용 |
| 응답 DTO | `dto.response`에 `PlaceResponse`처럼 용도가 드러나는 이름 사용 |
| DTO 구현 | 요청·응답을 분리하고 `record` 사용 |
| 의존성 주입 | `private final` 필드와 생성자 주입 |
| Service | 구체 클래스로 시작하고, 필요한 계약이나 구현 교체 요구가 생기면 인터페이스 분리 |
| Controller | 요청 바인딩·입력 검증·서비스 호출·HTTP 응답 구성 담당 |

API 요청·응답에는 DTO를 사용한다. 엔티티를 직접 API 응답으로 노출하지 않는다. Spring Data Repository의 `findById` 등은 프레임워크 명명 규칙을 따른다.

### Lombok과 엔티티

DTO와 엔티티에 `@Data`를 사용하지 않는다. 엔티티는 일반 클래스에 `@Entity`를 선언하고, 필요한 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.

| 대상 | 사용 기준 |
|---|---|
| Controller·Service | 단순 생성자는 `@RequiredArgsConstructor`, 로깅이 필요하면 `@Slf4j` |
| 일반 클래스 | 필요한 접근자에 `@Getter` 사용 |
| 엔티티 변경 | 클래스 전체 `@Setter`를 사용하지 않고 `changeName()`처럼 변경 의도를 드러내는 메서드 사용 |
| Builder | 인자가 많아 생성 의도를 읽기 어려운 경우 선택적으로 사용 |
| `equals`·`hashCode` | 엔티티의 식별 기준과 사용처를 보고 별도로 설계 |

코드 포맷터는 하나의 설정을 공유한다. 사용할 도구와 설정 파일은 첫 공용 기반 PR에서 정한다.

## 기본 컨벤션 자동 검사

운영 코드의 아래 규칙을 공통 테스트로 검사한다. 규칙은 한 번 작성해 모든 PR에서 재사용하며, 컨벤션이 바뀔 때 함께 수정한다. 아키텍처 검사는 main 컴파일 출력 전체를, 소스 검사는 `src/main/java`를 대상으로 한다. 테스트 코드·검증용 fixture는 검사 대상에서 제외한다.

| 검사 규칙 | 기존 근거 | 적용 대상과 예외 |
|---|---|---|
| 역할별 패키지 위치 | 위의 패키지 구조 | Spring Controller·Service·Repository, Spring Data Repository, JPA Entity는 `domain.<업무>.controller/service/repository/entity`와 그 하위에 둔다. `@MappedSuperclass`는 해당 업무의 `entity` 또는 `global.entity`에 둔다. 설정·예외 등 다른 공통 역할은 이 위치 검사로 제한하지 않는다. |
| DTO 위치 | 요청·응답 DTO 분리 | `domain.<업무>.dto` 아래 타입은 `dto.request` 또는 `dto.response`와 그 하위에 둔다. 일반 값 객체 record까지 DTO로 간주하지 않는다. |
| Controller의 Repository 직접 의존 금지 | Controller의 서비스 호출·응답 구성 책임 | Controller에서 Repository 역할 타입이나 `repository` 패키지 타입을 직접 참조하는지 검사한다. |
| Service의 Controller 의존 금지 | Controller·Service의 역할 분리 | Service에서 Controller 역할 타입이나 `controller` 패키지 타입을 직접 참조하는지 검사한다. 도메인 간 호출·의존성의 일괄 금지는 추가하지 않는다. |
| 생성자 주입 | `private final`과 생성자 주입 | 운영 코드의 필드·메서드에 붙은 `@Autowired`, `@Inject`, `@Resource`, `@PersistenceContext`, `@PersistenceUnit` 주입을 금지한다. 생성자 주입과 설정값의 `@Value`는 허용한다. 테스트의 필드 주입은 대상이 아니다. |
| 주입 의존성의 `private final` | 의존성 주입 기준 | Controller·Service의 생성자 인자와 같은 타입인 인스턴스 필드를 검사한다. Lombok 생성자도 포함한다. 정적 필드·로거, `@Value`, 기본형·enum·`java.lang` 값, 생성자 인자에 대응하지 않는 일반 상태 필드는 제외한다. 엔티티 필드 전체에 적용하지 않는다. |
| DTO의 `record` | DTO 구현 기준 | `dto` 패키지의 클래스·인터페이스와 중첩 선언을 검사한다. 보조 enum·애너테이션 선언은 허용한다. |
| DTO·엔티티의 Lombok `@Data` 금지 | Lombok과 엔티티 기준 | DTO와 `@Entity`·`@MappedSuperclass` 타입을 검사한다. |
| 엔티티 클래스 전체 Lombok `@Setter` 금지 | 엔티티 변경 기준 | `@Entity`·`@MappedSuperclass` 타입의 클래스 애너테이션을 검사한다. 필드 단위 setter는 이 규칙의 금지 대상이 아니며, 사용 의도는 리뷰에서 확인한다. |

ArchUnit은 컴파일된 운영 코드의 역할·의존성을 검사한다. JavaParser는 컴파일 후 사라지는 Lombok 애너테이션과 record 선언을 Java 21 구문 트리로 검사한다. 두 검사는 DB나 Spring 컨텍스트를 실행하지 않는다. 필드 주입 등 테스트에서 필요한 패턴을 허용하기 위해 운영 코드만 검사한다.

패키지 이름이나 생성자 인자 타입만으로 업무 소유권과 모든 의존성을 판별할 수는 없다. 소스 검사도 별도 심볼 해석기 없이 전체 이름·명시 import·와일드카드 import를 확인한다. 다른 파일에 있는 동명 애너테이션 등 복잡한 이름 해석은 자동 검사의 한계로 남는다. 검사 규칙과 허용·위반 예시는 [컨벤션 테스트](../src/test/java/com/hsu/oneclickmeet/global/convention/)에서 관리한다.

### 실행과 실패 처리

```bash
# DB 없이 컨벤션 검사와 규칙 자체의 허용·위반 예시 검증
./gradlew test --tests 'com.hsu.oneclickmeet.global.convention.*'

# 테스트 DB 설정 후 전체 빌드: 컨벤션 검사도 포함
./gradlew build
```

`test`는 운영 Java 소스도 입력으로 추적하므로, 바이트코드가 같아지는 소스 변경도 다시 검사한다. 실패 위치와 규칙은 Gradle 출력과 `build/reports/tests/test/index.html`에서 확인한다. 새 규칙을 추가하거나 예외를 바꿀 때는 허용·위반 예시를 함께 갱신한다.

CI의 기존 `Build and Test`는 `./gradlew build`를 실행한다. 컨벤션 테스트 실패는 빌드 실패로 전달되며, 필수 검사 조건은 본인 소유 영역의 PR에도 적용된다. 관리자 우회 권한과 기존 사람 승인·CODEOWNERS 기준은 유지한다.

### AI 보조 리뷰

업무에 맞는 코드 배치, 계층별 책임, Service 추상화 필요성, 엔티티 직접 응답, JPA·트랜잭션·동시성·테스트의 구체적인 위험은 코드 맥락과 함께 검토한다. 이 항목을 새 CI 강제 규칙으로 만들지 않는다.

[AI 보조 리뷰 지침](../.github/copilot-instructions.md)은 Copilot의 저장소 지침이며, 다른 AI 도구에도 같은 검토 기준을 전달할 수 있다. 파일 추가 자체가 도구 연결이나 자동 리뷰 활성화를 뜻하지 않는다. AI는 문제와 근거만 간결하게 제시하며, 교육 설명이나 팀 내 질문·조율 절차를 붙이지 않는다. AI의 리뷰 완료·승인·대화 해결 여부는 병합 조건으로 추가하지 않는다.

## API 요청과 공통 응답

| 항목 | 기준 |
|---|---|
| URI | 소문자 복수 명사: `/places`, `/members/{memberId}` |
| JSON 필드 | `camelCase`: `placeId`, `createdAt` |
| 응답 본문 | 성공·실패 모두 `success`, `data`, `error` 공통 래퍼 사용. Content-Type은 `application/json` |
| 일반 성공 | `200 OK` |
| 리소스 생성 | `201 Created`, 생성한 리소스의 식별자를 응답에 포함 |
| 본문 없는 성공 | `204 No Content`, 응답 본문과 공통 래퍼 없음 |
| 빈 값 | 값이 없는 응답 필드는 `null`, 빈 목록은 `[]` |
| 날짜 | 날짜만 표현할 때는 `YYYY-MM-DD` |
| 시각 | 실제 발생 시점은 UTC 기준 ISO 8601 문자열로 전달 |

목록의 페이지네이션 방식과 응답 필드는 첫 목록 API의 요구사항에 맞춰 정한다.

### 공통 응답 구조

클라이언트가 응답의 바깥 구조를 공통으로 처리할 수 있도록 JSON 응답을 같은 형식으로 감싼다. 각 API의 응답 DTO는 `data`에 담는다.

| 필드 | 기준 |
|---|---|
| `success` | HTTP `2xx`이면 `true`, `4xx`·`5xx`이면 `false`. 실제 HTTP 상태와 일치시킨다 |
| `data` | 성공 시 해당 API의 결과, 실패 시 `null` |
| `error` | 성공 시 `null`, 실패 시 오류 객체 |

`success`는 HTTP 상태와 중복되지만 클라이언트의 공통 응답 처리를 위한 편의로 유지한다. HTTP 상태는 응답 상태로 전달하고, 구체적인 업무 오류는 `error.code`로 구분한다.

리소스 생성 성공 응답 예시 — HTTP `201 Created`:

```json
{
  "success": true,
  "data": {
    "placeId": 1
  },
  "error": null
}
```

## 오류 응답

실패 응답도 공통 래퍼와 `application/json`을 사용한다. `success`는 `false`, `data`는 `null`로 반환하고, `error`에 다음 필드를 담는다. HTTP 상태는 실제 오류에 맞게 반환한다.

| 필드 | 기준 |
|---|---|
| `error.code` | 프론트가 처리 방식을 구분할 업무 오류 코드. `COMMON-0400`처럼 업무 영역과 HTTP 상태를 나타내는 형식 사용 |
| `error.message` | 사용자에게 보여줄 수 있는 오류 안내 문구 |
| `error.errors` | 입력 검증 실패 시에만 필드별 오류 목록 제공 |

입력 검증 실패 응답 예시 — HTTP `400 Bad Request`:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-0400",
    "message": "입력값을 확인해 주세요.",
    "errors": [
      {
        "field": "name",
        "message": "장소 이름은 필수입니다."
      }
    ]
  }
}
```

- 프론트엔드는 HTTP 상태로 성공·실패를 분류하고 `error.code`로 업무 오류를 분기한다. `error.message`는 표시·설명용이며 문구를 파싱해 분기하지 않는다.
- 입력 검증 실패는 `400`으로 응답한다.
- 예상하지 못한 오류는 `500`과 일반 안내 문구로 응답한다. 내부 예외·SQL·스택 정보는 서버 로그에 기록한다.
- 공통 오류 정의는 `global.exception`, 업무 오류 정의는 해당 도메인에 둔다. 공통 처리기가 동일한 응답 형식으로 변환한다.
- 같은 업무 영역·HTTP 상태에서 여러 오류를 구분해야 할 때의 세부 코드 규칙은 첫 도메인 오류 목록을 작성할 때 정한다.

## JPA와 DB

| 항목 | 기준 |
|---|---|
| 테이블 이름 | 복수형 `snake_case`: `members`, `places`, `saved_places` |
| 컬럼 이름 | `snake_case`: `created_at`, `place_id` |
| 엔티티 식별자 | Java 필드는 `Long id`, DB 컬럼은 `id` |
| API 식별자 | `placeId`처럼 대상을 명시 |
| PK 생성 | MySQL 기준 `GenerationType.IDENTITY` |
| 매핑 위치 | `@Id`, `@Column` 등을 필드에 선언 |
| Enum | `@Enumerated(EnumType.STRING)` 명시 |
| 생성·수정 시각 | Java는 `Instant`의 `createdAt`, `updatedAt`; DB는 `created_at`, `updated_at` |

생성·수정 시각은 Spring Data JPA Auditing의 `@CreatedDate`, `@LastModifiedDate`로 관리한다. 두 시각이 필요한 엔티티는 `BaseTimeEntity`를 공유하고, 생성 시각만 필요한 기록성 데이터는 별도로 다룬다. Auditing 인프라를 활성화하는 설정도 함께 구현한다.

- 실제 조회·변경 흐름에 필요한 방향으로만 연관관계를 연결한다.
- 연관관계 조회는 `LAZY`를 기본으로 선언하고, 필요한 데이터의 조회 방식은 쿼리별로 정한다.
- `CascadeType.ALL`, `orphanRemoval`은 부모·자식의 생성·삭제 책임이 명확할 때 선택한다.
- 필수값과 고유성은 애플리케이션 검증과 함께 DB의 `NOT NULL`, `UNIQUE` 제약으로 표현한다.
- Enum 상수 이름을 변경할 때는 기존 저장 데이터도 함께 고려한다.

[Spring Data JPA Auditing 문서](https://docs.spring.io/spring-data/jpa/reference/auditing.html)

## 로컬과 dev 환경

환경 구성은 다음으로 확정했으며, Docker 및 환경별 설정 파일 구현은 별도 작업이다.

| 환경 | 애플리케이션 | DB |
|---|---|---|
| 로컬 | IDE에서 실행 | Docker MySQL |
| dev | Docker로 실행 | 공유 RDS MySQL |

```text
프로젝트 루트
├── Dockerfile              # Spring 애플리케이션 이미지 빌드
├── compose.local.yaml      # 로컬 MySQL 실행
├── compose.dev.yaml        # dev 앱 실행, RDS 연결
└── src/main/resources
    ├── application.yaml
    ├── application-local.yaml
    └── application-dev.yaml
```

`application.yaml`에는 공통 설정을, 환경별 파일에는 해당 환경의 설정을 둔다. 실제 접속 계정·비밀번호는 실행 환경에서 주입하고 저장소에 기록하지 않는다. 로컬과 RDS의 MySQL 버전 계열, 문자셋·collation, 시간대 설정을 맞춘다.

### 초기 스키마 관리

- 초기 로컬 개발은 `spring.jpa.hibernate.ddl-auto: create-drop`으로 시작한다.
- 로컬 데이터는 재생성 가능한 개발 데이터로 다룬다. Docker 볼륨을 사용해도 `create-drop`이 실행하는 테이블 삭제를 막지는 못한다.
- Flyway는 현재 도입하지 않는다. 공유 dev 데이터를 보존하기 시작하기 전에 도입 여부를 판단한다.
- dev의 `ddl-auto` 값은 첫 배포 전에 별도로 정한다. 로컬의 `create-drop`을 공통 설정에 넣어 dev에 적용하지 않는다.
- `main` 병합 시 dev 자동 배포를 목표로 하며, 서버 리드가 추후 CI/CD를 구성한다.

[Spring Boot DB 초기화 문서](https://docs.spring.io/spring-boot/how-to/data-initialization.html)

## 구현하면서 정할 항목

| 항목 | 판단 시점 |
|---|---|
| 포맷터와 공유 설정 | 첫 공용 기반 PR |
| 같은 업무 영역·HTTP 상태의 세부 오류 코드 | 첫 도메인 오류 목록 작성 |
| 페이지네이션 방식과 응답 필드 | 첫 목록 API 설계 |
| dev의 `ddl-auto` | 첫 dev 배포 전 |
| Flyway 도입 여부 | 공유 dev 데이터를 보존하기 시작하기 전 |
| CI 검증과 자동 배포 설정 | 서버 리드의 CI/CD 구성 작업 |

트랜잭션 범위, 삭제 정책, 도메인 간 호출과 의존성은 각 기능의 요구사항에 맞춰 설계한다. 구현 중 공통 기준을 바꿔야 하면 관련 PR에서 이유와 영향을 설명하고 이 문서도 함께 갱신한다.
