# Round 3 — Chat Memory + conversationId

> 배달 상담 에이전트에 *대화 메모리*를 도입하고, *세션 격리*와 *영속화*를 정량 측정 + 검증.
> Round 2까지의 *단발 챗봇* 한계를 풀어내는 라운드.

**라운드 한 줄 메시지**: *Memory는 SOT가 아니다. 발화 보존이지 시스템 실재의 그림자가 아니다.*

---

## 산출물 (이 디렉토리 안)

| 파일 | 내용 |
|---|---|
| [`EXPERIMENT_LOG_QUEST1.md`](EXPERIMENT_LOG_QUEST1.md) | 1단계 — 100 trial (5 시나리오 × 20) + 8가지 발견 + 가설 검증 |
| [`EXPERIMENT_LOG_QUEST2.md`](EXPERIMENT_LOG_QUEST2.md) | 2단계 — `MAX_MESSAGES` 3값 × 10턴(300) + 30턴 누적(900) + 12 발견 |
| [`EXPERIMENT_LOG_QUEST3.md`](EXPERIMENT_LOG_QUEST3.md) | 3단계 — JDBC 영속성 검증 + 의사결정 트리 + 4 함정 |
| [`EXPERIMENT_LOG_QUEST4.md`](EXPERIMENT_LOG_QUEST4.md) | 4단계 — Observability (토큰 분해) + AI 코드 리뷰 (Workflow 3-agent) |
| [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md) | 1·2·3단계 자가 점검 *설계 결정 문서* 통합 |
| [`quest4-trace-evidence.txt`](quest4-trace-evidence.txt) | Memory 작동 간접 증거 (TRACE 측정) |

라운드 회고: [루트 `JOURNAL.md`](../JOURNAL.md)의 *Round 3* 섹션.

---

## 측정 데이터 종합

| 단계 | 측정 단위 | 총량 |
|---|---|---:|
| smoke | 2-turn × 10 trial | 10 |
| 1단계 | 5 시나리오 × 20 trial × 2 turn | 100 trial |
| 2단계 (10턴) | MAX 3값 × 10 repeat × 10 turn | 300 turn |
| 2단계 b (30턴) | MAX 3값 × 10 repeat × 30 turn | 900 turn |
| 3단계 | 단발 + 재시작 영속성 검증 | 2 케이스 (h2:mem / h2:file) |
| 4단계 | AI 코드 리뷰 | 3 agent, 83k tokens, 203s |

총 ~**1,300+ turn**.

raw JSONL: `.private/notes/round3/` (gitignored).

---

## 핵심 발견 (13개 압축)

### 1단계 (8개)

| # | 발견 | 정량 |
|---|---|---|
| 1 | Memory 작동률 = 지시 대명사 명시성에 비례 | "그거"(45%) → "아까 물어본"(70%) → "그거 말고 1235"(95%) |
| 2 | 세션 격리 완벽 | scn4 B 노출 0/20 |
| 3 | DELETE 완벽 | scn5 T2 1234 0/20 + Memory 0 + 응답 1.5s |
| 4 | reset 부수효과 #1 재현 신호 (Round 2 의문) | -25% (scn2 22.5% vs scn1·3·5 평균 29.8%) |
| 5 | reason hallucination 78% 완화 (가설 1) | Round 2: 9/9 → Round 3: 2/9 |
| 6 | 응답-실재 분리 *다양한 형태로 재발* (가설 2) | ETA 임의 변경, fresh "찾을 수 없음", Tool 미호출 등 |
| 7 | Raw JSON tool call 누출 (워밍업 단계) | scn1 trial 1·2 |
| 8 | 한·중·일 다국어 혼재 noise | starter description 다국어 가이드 부족 |

### 2단계 — 10턴 측정 (7개)

| # | 발견 |
|---|---|
| `MAX_MESSAGES` 바이트코드 검증 — 0 이하 시 `IllegalArgumentException`, SystemMessage 무조건 유지, ToolMessage 미저장 |
| Tool 호출률 윈도우 크기에 비례 (16% → 20% → 22%) |
| 옛 정보 회수 = MAX의 진짜 영향 (T8: max2 4/10 vs max20 10/10) |
| turn 7 "그거" → 1235 정확도 60% 천장 (시퀀스 맥락 무게가 시간 우선 rule 압도) |
| turn 10 요약이 Memory의 진가 (max2: 1라운드만, max20: 전체 흐름) |
| 응답 시간 = MAX 크기에 비례 (긴 컨텍스트 처리) |
| 1단계 +1200 토큰 미스터리 *부분 해결* (ToolMessage layer ~1500) |

### 2단계 b — 30턴 누적 (5개 신규)

| # | 발견 |
|---|---|
| 🚨 **MAX=20이 30턴 시점 토큰 최저** (V자 패턴): max2(4262) > **max20(3704)** < maxMAX(4055) |
| Tool 호출률 *반비례*: Memory 부족 → Tool 의존도 ↑ → ToolMessage 누적 ↑ → 토큰 폭증 |
| 요약 품질 = Memory 크기 (T30 응답 길이: 84자 < 148자 < 210자) |
| 시퀀스 반복이 max2 옛 정보 회수 *부분 보완* (T8: 10턴 40% → 30턴 70%) |
| turn 17·27 "그거" → 1235 *60% 천장* (시퀀스 맥락 무게) |

### 3단계 — JDBC 영속성 (저장소 3종 비교)

| 저장소 | 재시작 후 Memory | 검증 |
|---|:-:|---|
| InMemory | ❌ | 1단계 측정 |
| `jdbc:h2:mem` | ❌ | 영속 아님 (JVM 동거) |
| **`jdbc:h2:file`** | **✓** | 세션·메시지 보존 |

함정 4개: ① h2:mem이 영속이 아님 ② `embedded` ≠ h2:file ③ Spring AI 1.0 H2 schema 미제공 ④ `pkill -f` 광범위 매칭 사고.

### 4단계 — AI 코드 리뷰 (8 결함 + lesson 매핑)

Workflow tool 3-agent pipeline (naive → 결함 분석 → 개선):
- **CRITICAL 2개**: 세션 오염 (`session-N` 예측 가능), 멀티 인스턴스 미고려
- **HIGH 4개**: 동시성, 메모리 누수, 재시작 시 소실, 프라이버시
- **MEDIUM 2개**: 윈도우 미설정, conversationId 하드코딩

AI agent의 결함 짚음 = 1단계 보안 시뮬레이션 라인과 정확 일치.

---

## 가설 검증 결과

### 가설 1 — Memory가 reason hallucination 잡음

**결과**: **부분 검증** (78% 완화, 22% 잔존)
- Round 2 scn 4: 9/9 (100%) fallback "집 앞에 사람이 없어요"
- Round 3 scn 2: 2/9 (22%) fallback, 7/9 (78%) 합리적 reason
- 한계: USER 발화에 reason 단서 *없을 때*만 일부 fallback. Memory는 *발화 보존*은 잘하지만 *없는 단서 생성*은 못 함.

### 가설 2 — Memory가 응답-실재 분리 해결

**결과**: **부분 부정** (사용자 통찰 정량 검증)
- Memory ≠ SOT. 도메인 모델만이 SOT.
- 응답-실재 분리 *다양한 형태로 재발*: ETA 임의 변경 / fresh "찾을 수 없음" / Tool 미호출 / "이미 배달 완료" 환각
- *조건부* 해결: Memory + Prompt + LLM 자연어 일관성 *셋 다 필요*

---

## `MAX_MESSAGES = 20` — 정량 정당화

| 근거 | 출처 |
|---|---|
| 평균 상담 5~8라운드 + 25% 여유 (직관) | 1단계 설계 결정 |
| T8 옛 정보 회수 100% (10턴) | 2단계 측정 |
| **30턴 시점 토큰 *최저*** (V자 패턴) | 2단계 b 측정 |
| 요약 품질 적정 (148자 / T30 ms 적정) | 2단계 b T30 응답 |

**한 줄**: 1단계 직관 선택이 2단계 + 2단계 b 정량 측정으로 *양 옆 모두 추월하는 sweet spot* 확정.

---

## 코드 변경 (이번 라운드)

### 신규 (3)
- `src/main/java/com/baedal/support/TestResetController.java` — 측정 인프라 reset 엔드포인트
- `src/main/resources/org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql` — Spring AI 1.0 H2 schema (직접 작성)
- `round3/` 디렉토리 (이 산출물)

### 수정 (10)
- `build.gradle` — JDBC starter + H2 의존성
- `src/main/resources/application.yml` — `DataSourceAutoConfiguration` + `JdbcChatMemoryRepositoryAutoConfiguration` exclude (default 프로필 = InMemory 보장)
- `src/main/resources/application-jdbc.yml` — `h2:file` + `initialize-schema: always` + exclude 빈 배열 덮어쓰기
- `src/main/java/com/baedal/support/memory/ChatMemoryConfig.java` — 3 Bean 구현 + `@Profile("!jdbc")`
- `src/main/java/com/baedal/support/memory/SessionController.java` — GET messages / DELETE / GET ids
- `src/main/java/com/baedal/support/AssistantController.java` + `SupportController.java` — `X-Session-Id` 헤더 + `ChatMemory.CONVERSATION_ID` 주입
- `src/main/java/com/baedal/support/AssistantChatClientConfig.java` — `defaultAdvisors(memoryAdvisor, performanceAdvisor)`
- `src/main/java/com/baedal/support/domain/OrderMockService.java` — `resetForTest()` 메서드
- `JOURNAL.md` — Round 3 entry

총 변경 파일: **약 14개** (변경 파일 ≤ 20 규칙 충족).

---

## 측정 인프라

| 파일 (.private/notes/round3/) | 역할 |
|---|---|
| `measure-smoke.sh` | smoke 측정 (1-turn pair) |
| `measure-quest1.sh` | 1단계 시나리오 디스패처 |
| `run-all-scenarios.sh` | 1단계 5 시나리오 마스터 |
| `analyze-quest1.sh` | 1단계 정량 분석 |
| `measure-quest2.sh` | 2단계 10턴 시퀀스 (10 repeat) |
| `run-quest2.sh` | 2단계 MAX 3값 자동화 마스터 |
| `measure-quest2b.sh` | 2단계 b 30턴 시퀀스 (robust) |
| `run-quest2b.sh` | 2단계 b 마스터 |
| `run-quest2b-recovery.sh` | recovery (max2·max20 재측정) |
| `quest{1,2,2b}-*.jsonl` | raw 측정 데이터 (gitignored) |
| `bootrun-*.log` | Spring 로그 (Tool 호출, PerformanceLoggingAdvisor) |

워크트리 흔적: `round-3-jdbc` 브랜치 commit `46951e0` (port 8081에서 JDBC 영속성 검증). main으로 cp 완료 후 워크트리 *삭제 가능*.

---

## Round 3 미해결 — Round 4·5로 이월

1. **reset 부수효과 #1 원인 규명** — Ollama KV 캐시? HTTP 연결 풀? LLM 입력 신호 변화?
2. **turn 7·17·27 "그거" 정확도 60% 천장** — 시간 우선 rule 강화 또는 *상태 cross-check Advisor*
3. **응답-실재 분리 자동 검증** — Round 5 Guardrail 또는 *시스템 상태 cross-check Advisor*
4. **30턴+ 장기 대화의 토큰 폭증** — 요약(summarization) 전략 도입 (Round 5)
5. **Spring AI 1.0 H2 schema 미제공** — 후속 버전 출시 시 schema-h2.sql 제거 가능

---

## 다음 라운드 — Round 4: RAG (PgVector + QuestionAnswerAdvisor)

- *모르는 것을 답하게 하는 방법*
- Memory ↔ RAG의 차이: 세션 맥락 vs 지식 회수
- Advisor 체인의 진가 — MemoryAdvisor 옆에 QAdvisor

선행 학습:
- Docker `pgvector/pgvector` 띄우기
- Ollama `ollama pull nomic-embed-text`
- Spring AI `VectorStore`, `Document`, `EmbeddingModel`
