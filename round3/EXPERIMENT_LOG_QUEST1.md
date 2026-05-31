# Round 3 — 1단계 실험 로그

> QUEST 1단계 시나리오 5종 × 20회 = **100 trials**. Chat Memory 도입의 효과와 한계를 정량 측정.
>
> **raw JSONL**: `.private/notes/round3/quest1-scn{1..5}.jsonl`
> **측정 일시**: 2026-05-30 11:14~11:36 KST (1313초 = 21.9분)
> **bootRun**: 단일 세션 PID 51585. 시나리오 2만 매 trial 직전 `/api/v1/_internal/reset` 호출.

---

## 측정 조건 (Control)

- 모델: Ollama `qwen2.5:latest` (`http://localhost:11434`)
- `BaedalPrompt`: starter 버전 (`[Tool 사용 규칙]` + `[대화 맥락 사용 규칙]` 두 섹션 포함)
- `ChatMemoryConfig`:
  - `InMemoryChatMemoryRepository`
  - `MessageWindowChatMemory(maxMessages=20)`
  - `MessageChatMemoryAdvisor(order=10)`
- `OrderTools` description: starter 짧은 버전 (round-2 4요소 풀버전 아님)
- 시드: starter 6건 (1234 DELIVERING / 1235 CREATED / 1236 DELIVERED / 1237 COOKING / 1238 CANCELED / 1239 ACCEPTED)
- **시드 reset**: 시나리오 2만 매 trial 직전. `OrderMockService.resetForTest()` = `clear() + seed()`. seed가 `LocalDateTime.now()` 기준이라 ETA stale 자동 해결.

## 변수 분류

| 카테고리 | 변수 |
|---|---|
| Independent | 시나리오(1~5), sessionId 패턴(같은/다른), 발화 내용 |
| Control | 모델, description, BaedalPrompt, MAX_MESSAGES=20, 시드(reset로 격리) |
| Dependent | Tool 호출률, 응답 텍스트, Memory 메시지 수, 응답 시간 |
| Noise | qwen2.5 stochastic, 한국어 token decoding ("라이ダー"), 다국어 혼재 |
| Confounding | (해결) 시나리오 2 시드 누적은 reset로 격리. ETA stale은 seed `LocalDateTime.now()`로 자동 해결 |

---

## 시나리오 정의

| # | T1 | T2 | sid | reset | 기대 결과 |
|---|---|---|---|---|---|
| 1 | "2024-1234 어디쯤이에요?" | "그거 언제 도착해요?" | 같은 | X | T2: Memory에서 1234 추출 |
| 2 | "2024-1234 취소해주세요" | "아, 그거 말고 2024-1235 취소해주세요" | 같은 | 매 trial | T2: 취소 대상 1235로 전환 |
| 3 | "2024-1234 어디쯤이에요?" | "아까 물어본 그 주문 언제 도착해요?" | 같은 | X | T2: 변형 지시 대명사로도 Memory 추출 |
| 4 | A: "2024-1234 어디쯤이에요?" | B: "그 주문 어디쯤이에요?" | 다른 | X | B는 맥락 없음 (격리) |
| 5 | "2024-1234 어디쯤이에요?" → DELETE | "그거 몇 분 남았어요?" | 같은 + DELETE | X | T2: 맥락 사라짐 |

---

## 누적 결과 한눈에

| Scn | Tool 호출 | T1에 orderId | T2에 orderId | 응답 시간 T1/T2 | Raw JSON 누출 T1/T2 |
|---|---:|---:|---:|---:|---:|
| 1 | 12/40 (**30.0%**) | 5/20 | 9/20 (**45%**) | 8.3s / 9.2s | 1 / 1 |
| 2 | 9/40 (**22.5%**) | 7/20 | **19/20 (95%)** | 8.2s / 9.1s | 0 / 0 |
| 3 | 11/41 (**26.8%**) | 5/20 | 14/20 (**70%**) | 9.2s / 8.4s | 0 / 0 |
| 4 | 7/40 (**17.5%**) | 4/20 (A) | **0/20 (B)** ✓ | 8.6s / 8.9s | 0 / 0 |
| 5 | 13/40 (**32.5%**) | 6/20 | **0/20** ✓ | 6.0s / **1.5s** | 0 / 0 |
| **합** | **52 / 201 LLM (25.9%)** | 27/100 | 42/100 (B,5T2 제외) | — | **2/200** |

**Memory 메시지 수**:
- scn1·2·3: 4 msgs (USER×2 + ASSISTANT×2) 일관
- scn4: A 2 + B 2 (각각 격리)
- scn5: **0 msgs** (DELETE 정상 작동)

**총 Session clear**: 20회 (scn5의 DELETE 전부)

---

## 핵심 발견 8가지

### 발견 1 — Memory 작동률, 지시 대명사 형태에 강하게 의존

| 시나리오 | 지시 대명사 | T2에 orderId 등장 |
|---|---|---:|
| scn1 | *"그거"* | 9/20 (**45%**) |
| scn3 | *"아까 물어본 그 주문"* | 14/20 (**70%**) |
| scn2 | *"아, 그거 말고 2024-1235"* (1235 명시) | 19/20 (**95%**) |

→ **명시성 ↑ → Memory 작동률 ↑** (45% → 70% → 95%).
- *"그거"* 단독은 LLM이 *모호하다고 판단해 Tool 안 부름* 케이스 다수.
- *"아까 물어본"*처럼 시간/맥락 단어 추가 시 LLM의 Memory 참조 의도 강화.
- *"그거 말고 1235"*처럼 새 orderId까지 명시하면 거의 완벽.

→ Round 2 발견 *"description = LLM에게 보여주는 유일한 API 문서"* 의 **사용자 발화 버전**: *지시 대명사도 명시성 = 신호 강도*.

### 발견 2 — 세션 격리 완벽 (scn4)

- **B T2에 1234 노출 = 0/20 (0%)** ✓
- B Memory 평균 2 msgs (USER + ASSISTANT, A 영향 없음)
- B 응답 패턴 100% 일관: *"주문번호를 알려주시겠어요? 그 정보로 배달 위치를 확인해 드리겠습니다."*

**보안 사고 시뮬레이션 성공**. 발제 3.1의 *"심각한 개인정보 사고"* 가 X-Session-Id 격리로 차단됨.

### 발견 3 — DELETE 검증 완벽 (scn5)

- DELETE 직후 Memory 비어있음: **20/20** ✓
- T2 "그거" 응답에 1234 등장: **0/20** ✓ — LLM이 *맥락 못 찾음*
- T2 응답 시간: **1.5초** (다른 시나리오의 1/6) — Tool 안 부르고 짧은 응답 *"주문번호를 알려주시겠어요?"*

→ Memory 삭제가 *코드 + LLM 행동 양쪽*에서 완전 작동.

### 발견 4 — reset 부수효과 #1 재현 신호 (round-2 의문점)

| 시나리오 그룹 | Tool 호출률 |
|---|---:|
| reset 없음 (scn1·3·5) | 평균 **29.8%** |
| reset 매 trial (scn2) | **22.5%** |

**차이 ≈ -25%**. Round 2 LEARNING_LOG의 *"reset endpoint 호출 후 동일 발화의 Tool 호출률이 일관 감소"* 가 Round 3에서도 **방향성 재현**. 단 20 trials는 통계 검증으로는 약함 — 2단계에서 추가 측정.

### 발견 5 — reason hallucination *부분* 완화 (가설 1)

scn2 cancelOrder 9건의 reason 파라미터:

| reason | 빈도 | 출처 |
|---|---:|---|
| `"집앞에 사람이 없어요"` | 2/9 (**22%**) | ToolParam example fallback (Round 2 사고 그대로) |
| `"취소요청"` / `"즉시취소요청"` / `"고객 요청"` / `"고객님의 요청에 따라 주문을 취소합니다."` | 7/9 (**78%**) | 합리적 일반 reason |

- Round 2 scn 4: 9/9 (100%) 모두 example fallback
- Round 3 scn 2: 2/9 (22%) example fallback

→ **Memory가 reason hallucination을 *완화*하지만 *완전 해결*은 안 함**.
- 사용자 발화에 reason 단서가 없는 경우(예: *"2024-1234 취소해주세요"*) 일부 LLM은 여전히 example 베낌.
- 가설 1은 **조건부 검증** — *USER 발화에 reason 단서가 명시되어 있을 때*만 Memory가 풀어줌.

### 발견 6 — 응답-실재 분리, *다양한 형태로* 재발 (가설 2)

가설 2 ("Memory가 응답-실재 분리 해결")는 *Memory만으론 못 풀고 Prompt + LLM 자연어 일관성 필요* — 사용자가 직접 짚었듯 *"Memory ≠ SOT"*.

Round 3에서 관찰된 응답-실재 분리:

| 시나리오·trial | 사고 | 형태 |
|---|---|---|
| scn1 trial 10 | T1 ETA "11:40:55" → T2 ETA "11:58" | LLM이 Memory만 보고 *임의로 18분 변경* |
| scn2 trial 20 | reset 후 1235=CREATED인데 *"찾을 수 없습니다"* | LLM이 Tool 호출 안 하고 임의 응답 |
| scn2 trial 10 | "1235 상세 정보를 먼저 확인할까요?" | Memory 추출은 OK, Tool 호출 부재 — *말로만 행동* |
| scn3 trial 10 | T1 ETA "11:40:55" → T2 ETA "11:58" | scn1 trial 10과 동일 패턴 |

→ **응답-실재 분리는 Memory 도입 후에도 다양한 형태로 재발**. Memory가 *작동하는 경로*와 *환각하는 경로*가 trial별로 비결정적. Prompt 강화 + LLM 일관성 향상 없이는 풀리지 않음.

### 발견 7 — Raw JSON tool call 누출, scn1만 발생

| Scn | T1 누출 | T2 누출 |
|---|---:|---:|
| 1 | 1/20 | 1/20 |
| 2~5 | 0 | 0 |

scn1 trial 1·2에서만 발생. **bootRun 직후 초기 trial에 집중**된 것으로 보임 — Spring AI ollama 워밍업 단계의 비정상. smoke 측정 trial 6 case와 동일 패턴.

### 발견 8 — 한·중·일 다국어 혼재 noise 일관

지속적 발생 (scn2 trial 1, scn3 trial 15·20, scn4 trial 10 등):
- 한·중 혼재: *"罪송합니다, 이 주문은 이미 조리 과정에 들어갔습니다.因此，我们需要联系客服。"*
- 일본어 token 누출: *"라이ダー"*, *"라이der"*
- 영문 keyword: *"orderid: 2024-1234"*

starter description의 다국어 가이드 부족 영향. Round 2 v1 (한·중 혼재 1/10) 보다 빈도 약간 높음.

---

## 가설 검증 종합

### 가설 1 — Memory가 reason hallucination을 잡는다

**검증 경로**: scn2 cancelOrder 호출의 reason 파라미터

**결과**: **부분 검증** (78% 개선, 22% 잔존)
- Round 2: 9/9 동일 fallback → Round 3: 2/9 fallback
- 한계: 사용자 발화에 reason 단서가 없는 경우 fallback 가능. *Memory가 단서를 가져오지 못함*.

**진단**: Memory는 *발화 단서 보존*은 잘하지만, *발화에 없는 단서 생성*은 못 함 (당연). 진짜 해결은:
1. ToolParam example을 *덜 매력적으로* 작성 (LLM이 베끼지 않게)
2. Prompt에 *"reason은 사용자 발화 그대로"* 강제

### 가설 2 — Memory가 응답-실재 분리를 해결한다

**검증 경로**: scn2 trial 20 + scn1·3 trial 10 같은 응답-실재 분리 케이스

**결과**: **부분 부정** (사용자가 미리 짚은 통찰 그대로)
- Memory ≠ SOT. 도메인 모델만이 SOT.
- Memory는 *대화 스냅샷*이지 *시스템 실재의 그림자*가 아님.
- LLM이 Memory만 보고 추론하면 실재와 분리된 응답 가능.

**진단**: 가설 2가 *조건부로* 성립하려면 셋이 동시:
1. 1턴 LLM이 자연어 응답에 Outcome/시간 *정확히* 명시
2. Memory에 ASSISTANT 응답 저장 (자동)
3. 2턴 LLM이 그 응답을 *환각 없이* 재읽기

세 조건 다 LLM 의존. Memory + Prompt + 모델 일관성의 결합 산물.

---

## Round-2 미해결 의문점 #1 — reset 부수효과

**원본**: *"reset endpoint 호출 후 동일 발화의 Tool 호출률이 일관 감소 (1236 cancel 9/10 → 1/10); ChatClient는 stateless인데 원인 미규명"*

**Round 3 첫 신호**: reset 직후 단발 ping → Tool 호출 0

**Round 3 정량 측정 결과**:
- scn1·3·5 (reset X) 평균: 29.8%
- scn2 (reset O) : 22.5%
- **차이 -25%**, 방향성 일치

**진단**: round-3에서도 *Memory와 무관하게* reset 후 호출률 감소 신호. 원인 후보는 round-2 LEARNING_LOG의 *"Ollama KV 캐시 invalidate / HTTP 연결 풀 / LLM 수신 신호 미세 변화"*. round-3에서도 미규명. 4단계 Observability에서 ollama 측 cache hit 로그 추적 권장.

---

## 시나리오별 raw 데이터 (대표 trial)

### scn1 — 1234 → 그거 (20 trials)

| trial | T1 (ms) | T2 (ms) | 패턴 |
|---|---:|---:|---|
| 1 | 12905 | 8451 | T1 정상 Tool 호출 + ETA "11:24" → T2 Memory 재사용 |
| 5 | 12041 | 7890 | T1 ETA 응답 → T2 ETA 재사용 |
| 10 | 12230 | 8051 | T1 1234 명시 + ETA → T2 Memory 재사용 |
| 15 | 5981 | 11300 | **T1 "주문번호 알려주세요"(Tool 미호출)** → T2 *Memory에서 1234 추출 + Tool 호출 + ETA* (보완) |
| 20 | 6249 | 11397 | trial 15와 동일 패턴 (T1 실패 → T2 보완) |

### scn2 — 1234 취소 → 1235 취소 (20 trials, reset)

| trial | T1 (ms) | T2 (ms) | 패턴 |
|---|---:|---:|---|
| 1 | 12095 | 8315 | 한·중 혼재 응답. T1: NOT_CANCELABLE 안내, T2: 1235 취소 안내 |
| 5 | 6447 | 6959 | T1: 1234 확인 요청, T2: 1235 정보 확인 요청 |
| 10 | 11787 | 7600 | T1: NOT_CANCELABLE 정상 응답, **T2: Memory→1235 추출 + Tool 미호출** |
| 15 | 6871 | 7692 | "orderid: 2024-1234/1235" 영문 noise |
| 20 | 7896 | 7794 | **T2: 1235 실재=CREATED인데 *"찾을 수 없습니다"* — 응답-실재 분리** |

cancelOrder reason 분포: `"집앞에 사람이 없어요"` 2/9, 기타 합리적 reason 7/9.

### scn3 — 1234 → 아까 물어본 그 주문 (20 trials)

| trial | T1 (ms) | T2 (ms) | 패턴 |
|---|---:|---:|---|
| 1 | 14917 | 9929 | T1 정상 + ETA "11:40:55" → T2 *Memory 재사용 + 동일 시간* |
| 5 | 6097 | 6276 | T1 *"주문번호 알려주세요"* → T2 1234 추출 + Tool 호출 |
| 10 | 12254 | 8246 | T1 ETA "11:40:55" → T2 ETA "11:58" — **임의 변경 (응답-실재 분리)** |
| 15 | ? | ? | 한·중·일 혼재 (`라이ダー`, `还有什么我可以帮助您的吗`) |
| 20 | 6135 | 6276 | T1 *"주문번호 알려주세요"* → T2 Memory 추출 + Tool 호출 |

### scn4 — A:1234 → B:그 주문 (20 trials, 세션 격리)

| trial | A T1 (ms) | B T2 (ms) | 패턴 |
|---|---:|---:|---|
| 1 | — | — | A: *"주문번호 알려주세요"* (Tool 미호출), B: *"주문번호 알려주세요"* (격리 OK) |
| 5 | — | — | 동일 |
| 10 | — | — | A: 정상 Tool 호출 + ETA, B: *"주문번호 알려주세요"* (격리 OK) |
| 15 | — | — | 동일 |
| 20 | — | — | A: 정상 Tool 호출, B: 격리 OK |

→ **20/20 모두 B에서 1234 미노출**. 패턴 100% 일관.

### scn5 — 1234 → DELETE → 그거 (20 trials)

| trial | T1 (ms) | T2 (ms) | 패턴 |
|---|---:|---:|---|
| 1 | ? | 1105~ | T1: Tool 미호출, T2: *"주문번호 알려주세요"* (Memory 비어있음) |
| 5 | ? | ~ | T1: 정상 + ETA, T2: *"주문번호 알려주세요"* (DELETE 후 맥락 없음) |
| 10 | ? | ~ | 동일 |
| 15 | ? | ~ | 동일 |
| 20 | ? | ~ | 동일 |

→ T2 응답 평균 **1.5초** (Tool 안 부르고 짧은 응답). DELETE 검증 완벽.

---

## 다음 단계 — 2단계 (MAX_MESSAGES 비교)

QUEST 1단계 100 trials의 발견을 토대로 진입:

| 측정 대상 | 추가 가설 |
|---|---|
| MAX_MESSAGES = 2 | 발견 1 (Memory 작동률 인과)이 짧은 윈도우에선 어떻게? |
| MAX_MESSAGES = 20 | baseline (이번 측정과 일치) |
| MAX_MESSAGES = MAX_VALUE | 입력 토큰 선형 증가 추세 + reset 부수효과 누적? |

reset 부수효과 #1 정량 검증도 2단계에서 trial 수 늘려 통계 신뢰성 확보.

---

## 측정 인프라

| 파일 | 역할 |
|---|---|
| `measure-quest1.sh` | 시나리오 디스패처 (SCN 1-5) |
| `run-all-scenarios.sh` | 5 시나리오 순차 실행 마스터 |
| `analyze-quest1.sh` | 결과 분석 (시나리오별 정량 표) |
| `quest1-scn{1..5}.jsonl` | raw 데이터 (시나리오별) |
| `/tmp/bootrun.log` | Spring 로그 (Tool 호출, PerformanceLoggingAdvisor 토큰) |
| `/tmp/measure-master.log` | 마스터 실행 로그 |

총 측정 시간: 1313초 (21.9분)
