# Round 3 — 3단계 실험 로그

> QUEST 3단계 — InMemory → JDBC 전환 + 영속성 검증.
>
> **작업 위치**: `/private/tmp/loopers-quest3` 워크트리 (`round-3-jdbc` 브랜치, port **8081**)
> **측정 일시**: 2026-05-31 17:00~17:25 KST
> **bootRun jdbc**: `./gradlew bootRun --args='--spring.profiles.active=jdbc'` (port 8081)
> **이유 (worktree)**: main 8080에서 2단계 b 측정 진행 중이라 격리

---

## 코드 변경 (5개)

| 파일 | 변경 |
|---|---|
| `build.gradle` | `+ 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'`<br>`+ 'com.h2database:h2'` (runtimeOnly) |
| `src/main/resources/application.yml` | `+ server.port: 8081` (워크트리 격리) |
| `src/main/resources/application-jdbc.yml` | `url: jdbc:h2:file:./data/baedal-jdbc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`<br>`initialize-schema: always` |
| `src/main/java/com/baedal/support/memory/ChatMemoryConfig.java` | `@Profile("!jdbc")` on `chatMemoryRepository()` Bean |
| **신규** `src/main/resources/org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql` | H2 schema 정의 (Spring AI 1.0 starter가 *정확히 이 경로*에서 schema 찾음) |

---

## 발생한 문제 + 해결

### 문제 1 — `No schema scripts found at location 'classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql'`
**원인**: Spring AI 1.0 starter가 H2 schema 파일을 *기본 제공 안 함*. PostgreSQL·MySQL 등은 있지만 H2는 별도 작성 필요.
**해결**: `src/main/resources/org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql` 직접 작성.

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content         CLOB        NOT NULL,
    type            VARCHAR(10) NOT NULL,
    "timestamp"     TIMESTAMP   NOT NULL,
    CONSTRAINT TYPE_CHECK CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");
```

### 문제 2 — `Table "SPRING_AI_CHAT_MEMORY" not found` (h2:file 전환 후)
**원인**: `initialize-schema: embedded` 옵션이 **in-memory DB만 초기화**. `h2:file`은 embedded로 분류 안 됨 → schema 적용 안 됨 → 첫 SELECT에서 *테이블 없음*.
**해결**: `initialize-schema: always`로 변경 + 기존 깨진 `data/baedal-jdbc.mv.db` 삭제 후 재시작.

`CREATE TABLE IF NOT EXISTS` 라 *재시작마다 schema 적용*해도 데이터 안전.

---

## 영속성 검증 — 3가지 저장소 비교

| 저장소 설정 | 재시작 후 Memory 유지? | 검증 시점 | 비고 |
|---|---|---|---|
| **InMemory (기본)** | ❌ | 1단계 측정 (PID 변경 시 ConcurrentHashMap 소실) | JVM 종료 = 메모리 사라짐 |
| **`jdbc:h2:mem:...`** | ❌ | 2026-05-31 16:47 검증 | DB가 *JVM 동거*. 종료 시 사라짐 |
| **`jdbc:h2:file:./data/baedal-jdbc`** | ✅ | 2026-05-31 17:20 검증 | mv.db 파일 (32KB) 디스크 영속 |

### h2:file 검증 raw

**재시작 전**:
- 세션 ids: `["file-persist-test"]`
- messages 수: 2 (USER + ASSISTANT)
- 파일: `data/baedal-jdbc.mv.db` (16KB)

**재시작 후**:
- 세션 ids: `["file-persist-test"]` ✅
- messages 수: 2 ✅
- USER 응답 본문 일치 ✅
- ASSISTANT 응답 본문 일치 ✅
- 파일: `data/baedal-jdbc.mv.db` (32KB — 트랜잭션 로그 정리로 약간 증가)

---

## 의사결정 트리 — InMemory vs JDBC

| 운영 조건 | InMemory | JDBC (h2:mem) | JDBC (h2:file) | PostgreSQL |
|---|---|---|---|---|
| 멀티 인스턴스 (로드밸런서 뒤) | ❌ | ❌ | ❌ (단일 파일 lock) | ✅ |
| 서버 재시작 후 대화 이어짐 | ❌ | ❌ | ✅ | ✅ |
| 법적 N년 감사 보관 | ❌ | ❌ | △ (백업·아카이브 정책 필요) | ✅ |
| 단일 인스턴스 + 분 단위 짧은 세션 | **✅** | ✅ | (오버엔지니어링) | (오버엔지니어링) |
| H2 Console로 SQL 디버깅 | ❌ | ✅ | ✅ | (PgAdmin) |

### InMemory로 충분한 3가지 조건

1. **단일 인스턴스** + **짧은 세션** (분 단위로 끝남, 재시작 시 잃어도 OK)
2. **개발/데모** — 빠른 부팅, 설정 0
3. **테스트 자동화** — 매 테스트마다 fresh state 필요

### JDBC가 필요한 3가지 조건

1. **재시작 후 이어짐** — 배포·크래시·점검 시 고객 대화 보존
2. **멀티 인스턴스** — 로드밸런서가 라운드로빈으로 다른 서버 보낼 때도 같은 대화
3. **감사/추적** — 운영팀이 *"고객이 그날 봇한테 뭐라고 했나"*를 사후 조회

### 배달 실제 운영이라면 — **PostgreSQL** 추천
- Round 4에서 **PgVector를 띄울 예정** → 같은 PostgreSQL 인스턴스 공유 (DB 두 개 안 운영해도 됨)
- H2:file은 *단일 노드 limit* + *백업/HA 부족*
- PostgreSQL은 *멀티 인스턴스 + WAL 백업 + replica HA*

### JDBC 도입 시 동시 고려할 비기능 요구사항 3가지

1. **인덱스**: schema의 `(conversation_id, "timestamp")` 인덱스 필수. SessionController `get(sessionId)` 패턴이 *세션별 시간순 조회*라 인덱스가 결정적.
2. **TTL/파티셔닝**: 시간 지나면 누적. 정책 — *"90일 이전 세션 자동 삭제"* 같은 배치. PostgreSQL이면 `pg_partman` 같은 도구로 시간 파티션.
3. **암호화**: `content` 필드가 PII 평문. column 레벨 암호화 (예: pgcrypto) 또는 DB TDE. 5주차 Guardrail의 입력 마스킹과 결합.

---

## 운영 함정 — 발견·교훈

### 함정 1 — `h2:mem`을 *영속*으로 착각

`jdbc:h2:mem:baedal;DB_CLOSE_DELAY=-1`의 `DB_CLOSE_DELAY=-1`은 *JVM 종료 전까지 DB 안 닫음*. 하지만 *JVM 자체가 죽으면* DB도 사라짐. **"영속화"의 의미 = JVM 외부 저장**. h2:mem은 영속이 아님.

### 함정 2 — `initialize-schema: embedded` ≠ h2:file 자동 초기화

이름이 *"embedded"*라 H2 같은 *내장 DB는 다 자동 초기화* 같지만, Spring Boot의 *embedded* 분류는 **데이터가 JVM 메모리에 있는지** 기준. h2:file은 *파일에 있어서 외부 영속성으로 분류* → embedded 안 함. **`always`가 안전한 default**.

### 함정 3 — schema-h2.sql 미포함 (Spring AI 1.0 GA)

Spring AI 1.0 GA의 `spring-ai-starter-model-chat-memory-repository-jdbc`가 H2 schema 파일을 *기본 제공 안 함* (PostgreSQL·MySQL·MariaDB는 제공). H2로 운영하려면 *직접 작성 필수*. 후속 버전에서 추가될 가능성.

### 함정 4 — `pkill -f BaedalSupportApplication`의 광범위 매칭

이번 라운드에서 *치명적 실수* — JDBC 8081 죽이려고 `pkill -f BaedalSupportApplication` 호출했더니 *main 8080 bootRun까지 죽임*. 그 시점에 main 측정 진행 중이었어서 *max20 결과 전체 손실*. **PID 명시 `kill $(lsof -ti tcp:8081)` 사용 권장**. process name pattern matching은 *동일 이름 multi-instance 환경*에서 위험.

---

## 자가 점검 체크리스트

- [x] JDBC 프로필로 bootRun 성공 (8081)
- [x] schema-h2.sql 작성 (Spring AI 1.0 H2 미지원 우회)
- [x] `initialize-schema: always`로 h2:file schema 보장
- [x] 재시작 후 h2:mem 사라짐 / h2:file 유지 검증
- [x] InMemory vs JDBC 의사결정 트리 작성
- [x] **H2 Console SQL 캡처** — H2 Shell tool로 SQL 직접 실행. 결과 보존: [`quest3-h2-console-sql.txt`](quest3-h2-console-sql.txt)
- [ ] 시나리오 5종 jdbc 프로필 재실행 (옵션 — *Memory 동작 코드는 동일*하므로 *InMemory 1단계 결과와 같을 것* 예상)

## H2 Console SQL 결과 (자가 점검)

브라우저 H2 Console 대신 **H2 Shell tool**로 SQL 직접 실행:

```sql
SELECT conversation_id, type, "timestamp", LEFT(content, 80) AS content_preview
FROM SPRING_AI_CHAT_MEMORY ORDER BY "timestamp";
```

결과:
```
CONVERSATION_ID   | TYPE      | timestamp               | CONTENT_PREVIEW
file-persist-test | USER      | 2026-05-31 17:19:56.198 | 2024-1234 어디쯤이에요?
file-persist-test | ASSISTANT | 2026-05-31 17:19:56.199 | 현재 2024-1234 주문은 배달 중이며 ...
(2 rows, 4 ms)
```

스키마 확인:
```
FIELD           | TYPE                   | NULL
CONVERSATION_ID | CHARACTER VARYING(36)  | NO
CONTENT         | CHARACTER LARGE OBJECT | NO
TYPE            | CHARACTER VARYING(10)  | NO
timestamp       | TIMESTAMP              | NO
```

→ USER × 1 + ASSISTANT × 1 = 2 rows. **ToolMessage 없음** (발제 4.5 + 바이트코드 검증 일치). 재시작 후에도 행 유지 (h2:file 영속성).

---

## 워크트리 상태

```
/private/tmp/loopers-quest3 (branch: round-3-jdbc)
  - 변경 7개 파일 (commit 안 됨)
  - bootRun PID 3887 살아있음 (port 8081, h2:file)
  - h2:file 데이터: /private/tmp/loopers-quest3/data/baedal-jdbc.mv.db (32KB)
```

### 워크트리 commit 또는 main으로 merge?
- 워크트리에서 *commit round-3-jdbc* 후
- main 디렉토리에서 *cherry-pick* 또는 *merge round-3-jdbc → round-3*

**주의**: main의 ChatMemoryConfig가 round-3-jdbc와 동일 영역 변경 (현재는 `MAX_MESSAGES = 20` 복원 후) → 충돌 없을 듯. 단 *@Profile 추가는 round-3-jdbc만*. cherry-pick 시 적용.

---

## 산출물

| 파일 | 역할 |
|---|---|
| `EXPERIMENT_LOG_QUEST3_DRAFT.md` | 이 문서 |
| 워크트리 코드 변경 7개 (위 표) | JDBC 동작 구현 |
| 영속성 검증 결과 raw (콘솔 캡처) | bootrun-jdbc.log, bootrun-jdbc-2.log |

---

## 다음 단계

QUEST 4단계 — Observability + AI 코드 리뷰.
