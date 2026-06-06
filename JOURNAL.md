# JOURNAL — Spring AI Agent 학습 회고

> 주차별 작업 히스토리 요약과 개인 회고/의견을 누적 기록한다.
> 상세 작업 기록·과제 원문은 `.private/` 에 비공개 보관하며, 이 문서에는
> 정제된 히스토리와 개인 의견만 남긴다. (git에 포함되는 유일한 기록 문서)

## Round 1 — Spring AI 기반 배달 상담 챗봇 만들기

- 기간: 2026-05-13 ~ 2026-05-16
- 한 일: System Prompt 가드레일 4종, `SupportResponse` 11필드 Structured Output, `PromptLabController`로 정량 비교, 동기 vs 스트리밍, `PerformanceLoggingAdvisor`, AI 코드 리뷰.
- 배운 점 / 개인 의견:
  - 가드레일·구조화·스트리밍을 트레이드오프로 보게 된 게 컸다. \"AI 시대에도 우리는 여전히 비즈니스 가치를 위해 트레이드오프를 한다\"는 감각.
  - `@JsonPropertyDescription` 같은 스키마 층이 System Prompt 길이보다 분류 안정성에 더 결정적이라는 측정 결과.
- 막힌 점 / 해결: AI 코드 리뷰 단계에서 \"진짜 결함\"이 보안·접근통제(IDOR, conversationId 탈취)에 있다는 걸 깨닫는 데 시간이 좀 걸림. 표면 결함(키 하드코딩 등) 위주로 보다가 늦게 발견.

## Round 2 — Tool Calling으로 주문/배달 시스템 연동

### 1단계 — Tool 3개 구현 + Mock 데이터 확장

- 기간: 2026-05-23 ~ 2026-05-24
- 한 일:
  - `OrderTools`에 `getOrderDetail`/`getDeliveryStatus`/`cancelOrder` 세 `@Tool` 구현. description은 4요소(무엇/언제/입력/실패) + detail/delivery 비대칭(delivery가 모호 발화 기본값, detail은 구체 키워드만)으로 작성.
  - `OrderMockService`에 시드 4건 추가(1236 DELIVERED / 1237 COOKING / 1238 사전 CANCELED / 1239 ACCEPTED). 1238은 `cancel()` 호출로 `canceledReason`까지 채워서 2단계 멱등성 실험 전제 준비.
  - `AssistantController`·`SupportController` 모두 생성자에서 `ChatClient` 한 번만 빌드해 `.defaultTools(orderTools)` 등록 — 강의 2.5.1 \"흔한 함정\"(매 요청 builder.defaultXxx 누적) 회피.
  - 시나리오 5종 × 10회 × 5 baseline = **250 trial 데이터**를 raw JSONL로 보존(`.private/notes/round2/quest1-v{1..5}-*.jsonl`). 분석은 `EXPERIMENT_LOG.md` 참조.
- 배운 점 / 개인 의견:
  - **단발 측정의 함정**. 첫 단발 테스트로는 \"40% 호출\"이었는데 10회 반복 평균은 84%. 신뢰구간 없는 단발은 의미 없다.
  - **System Prompt 만지작거리지 말기**. v2~v5 다섯 번 변형, 다섯 번 회귀. 정책 한 줄이 description 효과를 가린다는 강의 메시지를 데이터로 본 게 가장 컸음.
  - **`@ToolParam` 예시는 hallucination fallback이 된다**. scn 4 9/9가 동일 가짜 사유로 호출된 패턴은 단순 호출률 지표로는 못 잡는 데이터 품질 버그. 운영 환경에선 더 무서울 듯.
  - **\"안 되는 거 알면서 계속\" 안티패턴**을 학습. 5번째 회귀 시점에 멈추고 v1으로 원복하는 결정. 가설 부정 결과 자체가 강한 증거.
- 막힌 점 / 해결:
  - 가장 약한 곳(scn 2의 5/10)을 잡으려는 시도가 다 회귀로 끝남. 처음에는 \"부정 조건이 문제일 것\"이라고 추정했으나(v3 가설), 부정 제거가 더 떨어뜨리며 기각. 그 다음 \"`@ToolParam` 예시 hallucination을 sentinel로 잡으면 개선될 것\"(v4) 도 기각. \"트리거 문구를 좁히면\"(v5)도 기각. 결국 *현재 setup에서 84%가 사실상 상한*이라는 결론에 도달.
  - 결론: 더 끌어올리려면 System Prompt 외의 다른 lever(모델 자체, description 구조, Tool 등록 순서 등)가 필요. 1단계 범위에서는 84%를 baseline으로 인정하고 매듭.

### 2단계 — 멱등성 관찰

- 기간: 2026-05-24 ~ 2026-05-25
- 한 일:
  - `cancelOrder`의 Outcome 4종 (`CANCELED`/`ALREADY_CANCELED`/`NOT_CANCELABLE`/`NOT_FOUND`) 50회 측정.
  - 멱등성 분기 제거 실험을 *2단계*로 확장: Stage A (`ALREADY_CANCELED` 제거) + Stage B (`ALREADY_CANCELED` + `NOT_CANCELABLE` 둘 다 제거). 각 10 pair × 2회 cancel = 80 trial.
  - 측정 편의를 위해 `TestResetController` (`/api/v1/_internal/reset`) 임시 추가, `OrderMockService.resetForTest()` 도입.
- 배운 점 / 개인 의견:
  - **NOT_CANCELABLE이 *두 번째 안전망*** — Stage A에서 `ALREADY_CANCELED` 제거해도 `isCancelable()` 체크가 두 번째 cancel을 차단해 `canceledReason` 덮어쓰임이 안 일어남. 두 겹 방어의 가치 확인.
  - **Stage B pair 1·5에서 `canceledReason` 덮어쓰임 직접 캡처**. pair 1에서 *시스템은 새 reason으로 갱신됐는데 LLM은 \"이미 취소\"라고 거짓 응답* — 응답 표면 ↔ 시스템 실재 분리라는 가장 위험한 패턴을 데이터로 처음 봤음.
  - Outcome 4개의 자연어 친화성이 *AI에 확실한 답변을 유도*하는 데 결정적임을 baseline pair 4의 응답으로 입증.
- 막힌 점 / 해결:
  - QUEST는 `ALREADY_CANCELED` 분기만 제거해도 `canceledReason` 덮어쓰임이 일어날 거라 기대한 듯한데, 우리 코드 구조에선 NOT_CANCELABLE이 안전망으로 잡아줘서 단일 분기 제거로는 *진짜 망가짐*을 못 봤다. 둘 다 제거(Stage B)하기로 결정.
  - reset endpoint 도입이 Tool 호출률을 떨어뜨리는 부수효과 발견 (의문점으로 남겨둠).

### 3단계 — Tool description 정량 비교

- 기간: 2026-05-25
- 한 일:
  - `getDeliveryStatus`의 description을 3가지 버전(A 강의 자료 / B 빈약 한 줄 / C 오해 유발 한 줄)으로 바꿔가며 동일 발화 \"주문번호 2024-1234 배달 어디쯤이에요?\"를 10회씩 호출 (총 30 trial).
- 배운 점 / 개인 의견:
  - **description 짧아질수록 호출률 단조 감소** — 4요소 풀버전 10/10 → A(강의 자료 4줄) 5/10 → B(한 줄) 0/10 → C(오해) 0/10. 가장 명확한 정량 데이터.
  - **C(오해)가 B(빈약)와 동일 결과** — *틀린 광고든 빈약한 광고든 LLM 입장에서는 같은 \"쓸 수 없는 Tool\"*.
  - A의 NONE 응답 5건이 1단계 v1 scn 2의 \"주문번호를 알려주시겠어요?\" 모방 패턴과 *완전 동일* — *부족한 description의 실패 모드는 모두 같은 자리로 수렴*.
- 막힌 점 / 해결: 발화가 1단계와 살짝 다른 점(\"어디쯤에 있어요?\" vs \"어디쯤이에요?\")이 정확한 비교에 영향 줄 수 있음을 인지하고 데이터 옆에 명시.

### 4단계 — Observability + AI 코드 리뷰

- 기간: 2026-05-25
- 한 일:
  - `AssistantController`에 `SimpleLoggerAdvisor`를 *임시* 추가해 1차 LLM 호출 prompt 전문 캡처 후 제거.
  - `ChatController`에 `PerformanceLoggingAdvisor` 영구 추가 (Round 1에 누락된 부분, 토큰 비교 baseline 확보).
  - 3 시나리오 × 10 trial = 30 trial 토큰 측정 + Tool 호출 trial 2건 별도 캡처. `/api/v1/chat`(32 토큰) vs `/api/v1/assistant`(1217 토큰) vs Tool 호출 시(2586 토큰) 정량 비교.
  - AI 코드 리뷰 — \"Spring AI 1.0으로 배달 주문 취소 Tool을 만들어줘\"로 받은 코드에서 결함 3개((a) 로깅 없음, (c) 동시성 보호 없음 `@Transactional`만, (S1) NOT_FOUND vs ACCESS_DENIED enumeration)와 개선 코드 작성.
- 배운 점 / 개인 의견:
  - **Tool 등록만으로 +38배 토큰, 호출 시 +81배** — 정확도와 토큰 비용의 trade-off 곡선이 매우 가파름. 어떤 Tool을 등록할지·description을 얼마나 자세히 쓸지가 운영 의사결정의 핵심.
  - **2차 LLM round-trip은 `SimpleLoggerAdvisor`에 보이지 않지만 토큰 점프(1235 → 2586)로 입증** — 직접 관찰 못 하는 메커니즘도 *간접 신호*로 확인 가능.
  - **AI 코드 리뷰의 진짜 결함은 *운영급(로깅·동시성)*과 *보안(Enumeration)*에 있음** — Round 1에서 본 \"표면 결함보다 보안·접근통제가 진짜\"의 연장선. Round 1 피드백 \"AI 코드 리뷰가 피상적이었던 케이스 부분 점수 1위\"를 의식해 깊이 있는 분석으로 갔음.
- 막힌 점 / 해결:
  - 4단계 측정 환경에서 Tool 호출률이 1단계 v1 대비 매우 낮아짐(매 trial reset 영향 추정). 토큰 측정에는 호출 안 한 trial도 의미 있어 그대로 보존하고 의문점으로 남김.

## Round 2 전체 매듭

- 측정 데이터: 1단계 250 + 2단계 80 + 3단계 30 + 4단계 40 = **400 trial** 보존.
- 산출물: README.md (모든 단계 raw 데이터 + AI 코드 리뷰 통합), `EXPERIMENT_LOG{,_QUEST2,_QUEST3,_QUEST4}.md`, `DESIGN_DECISIONS.md`(Q1~Q10), `LEARNING_LOG.md`(1단계 + 2·3·4단계 통합 회고).
- 변경 파일 정확히 20개 — QUEST 한계 충족.
- 가장 큰 학습: *측정 사이클*과 *부정적 결과의 가치*. 5번 회귀한 가설 검증 흐름이 가장 강한 산출물.


> 사용자가 정제 후 정식 JOURNAL.md에 추가하는 draft.
> Round 2 entry 스타일 따름.

---

## Round 3 — Chat Memory + conversationId

- **기간**: 2026-05-28 ~ 2026-05-31 (진행 중, recovery 마무리 단계)
- **한 일** (4단계 + JDBC 워크트리):
  - **1단계** — Memory 3레이어 구현 (`InMemoryChatMemoryRepository` + `MessageWindowChatMemory(20)` + `MessageChatMemoryAdvisor(order=10)`), `SessionController` 3 endpoint, `X-Session-Id` 헤더 + `ChatMemory.CONVERSATION_ID` 주입. 시나리오 5종 × 20 trial = **100 trial** 측정 (21.9분). reset 인프라(`TestResetController` + `OrderMockService.resetForTest()`)도 Round 2에서 가져와 부활.
  - **2단계** — `MAX_MESSAGES` 3값(2/20/`Integer.MAX_VALUE`) × 10 repeat × 10 turn = **300 turn** 측정 (26.7분). `MessageWindowChatMemory` 바이트코드 분석으로 *0 이하 시 `IllegalArgumentException`*, *SystemMessage 무조건 유지*, *ToolMessage 미저장* invariant 확정.
  - **2단계 b** (장기 누적 효과) — 30턴 시퀀스 × 10 repeat × 3값. maxMAX 정상(10/10)이지만 max2(2/10)·max20(0/10) 손실 → 원인은 *내 `pkill -f BaedalSupportApplication`이 main bootRun까지 죽인 실수* + script fragility 결합. measure-quest2b.sh를 robust화(curl 실패 fallback, JSONL line 항상 보장)한 뒤 max2·max20만 재측정 (recovery 진행 중).
  - **3단계 (JDBC, 워크트리)** — `/private/tmp/loopers-quest3` 워크트리(port 8081)에서 `spring-ai-starter-model-chat-memory-repository-jdbc` + H2 의존성 추가, `ChatMemoryConfig.chatMemoryRepository`에 `@Profile("!jdbc")`. **`schema-h2.sql`을 직접 작성**해야 함을 발견 (Spring AI 1.0이 PostgreSQL·MySQL schema는 제공하지만 H2는 미포함). `initialize-schema: embedded`는 *in-memory만 적용*이라 *h2:file*은 `always` 필요. h2:mem 재시작 시 소실 / h2:file 재시작 후 유지 둘 다 검증.
  - **4단계** — Observability: 토큰 분해 종합 (baseline ~1100 + ToolMessage layer ~1500 + USER/ASSISTANT layer ~100-1000). 1단계의 *+1200 미스터리*를 *ChatClient 내부 tool calling history*로 완전 해결. AI 코드 리뷰는 **Workflow tool 3-agent pipeline** (naive 생성 → 결함 8개 분석 → 개선 코드 + lesson 매핑) 사용.

- **배운 점 / 개인 의견**:
  - **메모리 기반 vs DB 영속화는 운영 방식에 따라 정답이 다르다** — 세션별 식별 (InMemory) vs DB 영속화 (JdbcChatMemoryRepository) 중 어느 게 옳다고 단정할 수 없음. 단일 인스턴스 + 짧은 세션이면 InMemory가 합리, 멀티 인스턴스·재시작 보존·법적 감사가 필요하면 JDBC. *운영 조건이 선택을 강제*하지 *기술 자체*가 우열을 가르지 않음.
  - **`MAX_MESSAGES = 20`은 단순 예측치가 아니라 실제 검증값** — 너무 많은 것(MAX_VALUE)도 너무 적은 것(MAX=2)도 좋지 않고 *20개가 적당한 크기*. 2단계 10턴 측정 + 2단계 b 30턴 누적에서 *V자 sweet spot* (max2 4262 > max20 3704 < maxMAX 4055 토큰) + 옛 정보 회수 (T8: max2 4/10 vs max20 10/10) 둘 다로 확인.
  - **리서치로 다른 사람들과 비교 + 새로운 인사이트** — 우리 *지시 대명사 해결 70%* ("아까 물어본 그 주문")가 *production-ready*인지 외부 검증 필요. 산업 SLA 리서치 결과 *read ≥75% / write ≥90% + HITL*이 배달 도메인 권장 — 우리 70%는 *경계선* (산업 권장보다 약간 낮음). AI draft만으로는 *합리적 추정*에 그치고, 외부 자료가 *기준 자체*를 조정하는 데이터를 줌.

- **AI 추가 통찰** (EXPERIMENT_LOG·DESIGN_DECISIONS에 상세):
  - 가설 1·2 검증 — 가설 1 *조건부 검증* (78% 완화), 가설 2 *부분 부정* (Memory ≠ SOT)
  - **+1200 토큰 미스터리** = ToolMessage layer (Memory 외부) — 4단계 TRACE prompt 캡처로 직접 증명
  - **측정 인프라 사고** — `pkill -f` 광범위 매칭으로 main 측정 손실 → PID 명시 default + script robust 패턴 학습
  - **Workflow tool로 AI 코드 리뷰 자동화** — 3 agent sequential. 사람이 *프롬프트만* 작성하고 *결함 8개 + 개선 코드 + lesson 매핑*까지 자동. AI 발견 8 결함 중 CRITICAL 2개(세션 오염·멀티 인스턴스)가 1단계 보안 사고 시뮬레이션과 정확 일치

- **막힌 점 / 해결**:
  - **+1200 토큰 미스터리 (1단계 발견 5)** — SessionController는 USER/ASSISTANT 4개만 보여주는데 T2 입력 토큰 +1200. Spring AI library 바이트코드 분석 (Explore agent) + 2단계 MAX 3값 비교로 *ToolMessage layer (Memory 외부)*가 원인 확정. 학습 가치 큼.
  - **JDBC `Table not found` (3단계)** — Spring AI 1.0 starter가 H2 schema 미포함. `src/main/resources/org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql` 직접 작성으로 해결.
  - **`initialize-schema: embedded` ≠ h2:file** — h2:file은 *파일 영속*이라 *embedded 미분류*. `always`로 강제.
  - **`pkill -f BaedalSupportApplication`이 main bootRun까지 죽임** — main 측정의 max2 8건 손실 + max20 전체 손실 → recovery 필요. 교훈: PID 명시.

- **의문점**:
  - **턴을 더 늘리면 (50턴·100턴) 어떻게 될지** — 2단계 b는 30턴까지 측정. *50턴+에서 토큰 폭증* 예상이지만 *정확한 곡선·sweet spot 변화*는 미측정. 발제 4.6 *"100턴이면 18,000 토큰"* 추정도 *직접 검증* 안 됨.
  - **MAX_MESSAGES 1개씩 증가 (18, 17, 16 등)도 채택 가능했는지** — 측정은 *2 / 20 / MAX* 3값만. *18·17·16처럼 미세 변화*가 토큰·정확도에 *어떤 영향*인지 안 봄. 20이 정말 *최적*인지, 아니면 *18·22도 동등*한지 미확인.
  - **데이터 쌓이면 환각 ↑** — Workflow 리서치로 *Lost in the Middle (arxiv 2307.03172)* 및 *환각 인과 분석 (arxiv 2510.20229)* 확인. 그런데 *어떻게 해결*할지가 의문. 요약 전략으로 *완화*는 가능하지만 *근본 해결*은 없는 듯.
  - **Round 3 측정 자체에서 남은 의문** (기존):
    - reset 부수효과 #1 (Round 2 미해결) — Round 3에서 *방향성 일치* (-25%)이나 원인 미규명 (Ollama KV 캐시? HTTP 연결 풀? LLM 입력 신호?)
    - turn 7·17·27 "그거" 정확도 60% 천장 — 시퀀스 맥락 무게 vs 시간 우선 rule
    - 응답-실재 분리 다양한 형태 재발 — Round 5 Guardrail 또는 상태 cross-check Advisor

- **Round 4에 시도하고 싶은 것**:
  - **Memory + RAG 활성화로 데이터 기반 AI 답변** — Round 4 PgVector + QuestionAnswerAdvisor. 환불 정책·메뉴 정보 등 *도메인 지식*을 RAG로 끌어와 Round 3에서 *Tool 미호출로 못 풀던 응답-실재 분리*를 *지식 기반*으로 해결.
  - **여기서 생길 보안 취약점 테스트 + 막기** — RAG 도입 시 prompt injection (OWASP LLM01) 공격 표면 확장. Round 4 + 5단계 Guardrail에서 *prompt injection 시뮬레이션 + allowlist·output filter 방어* 직접 테스트. Round 3에서 CVE-2026-41712 시뮬레이션 + Workflow AI 코드 리뷰 8 결함 학습한 흐름을 *Round 4 RAG·벡터 검색 영역*으로 확장.

## Round 3 전체 매듭

- 측정 데이터: 1단계 100 trial (21.9분) + 2단계 300 turn (26.7분) + 2단계 b 30턴 누적 900 turn (recovery 포함 약 1.6시간) + 3단계 JDBC 영속성 검증 + 4단계 Observability + AI 코드 리뷰 (Workflow). 총 ~1,300+ turn.
- 산출물 (`round3/` 디렉토리 7개):
  - `README.md` — Round 3 entry point + 13 핵심 발견 + 가설 검증
  - `EXPERIMENT_LOG_QUEST1.md` — 1단계 8 발견 + 가설 검증
  - `EXPERIMENT_LOG_QUEST2.md` — 2단계 12 발견 + +1200 미스터리 해결 + 30턴 V자 패턴
  - `EXPERIMENT_LOG_QUEST3.md` — 3단계 영속성 + 의사결정 트리 + 4 함정 + H2 Shell SQL 캡처
  - `EXPERIMENT_LOG_QUEST4.md` — Observability + AI 코드 리뷰 + Memory prompt TRACE 캡처
  - `DESIGN_DECISIONS.md` — 1·2·3단계 10 질문 모두 답 (Workflow 6회 리서치로 검증)
  - `quest3-h2-console-sql.txt` + `quest4-trace-evidence.txt` + `quest4-prompt-payload.txt` — 자가 점검 raw 보존
- 워크트리 (`/private/tmp/loopers-quest3`, `round-3-jdbc` 브랜치 commit `46951e0`) — main(round-3)에 cp 완료. 워크트리 *추후 삭제 가능*.
- 변경 파일 수: 약 19개 (코드 11 + round3/ 7 + JOURNAL.md) — 20개 한도 충족.
- **가장 큰 학습 (사용자 답)**: *Memory 기반 vs DB 영속화의 정답은 운영 방식에 따라 다르고, MAX_MESSAGES = 20은 단순 예측이 아닌 실제 검증값이며, 리서치로 외부 기준과 비교해 새로운 인사이트를 얻는 흐름이 정량 측정 학습의 핵심.*
- **방법론적 학습**: *가설을 정량 측정으로 완전 검증 / 부분 검증 / 부분 부정으로 나눠 결론낸 흐름* + *AI draft 비판·수정 → 리서치 보강 → 본인 답으로 완성*의 3축 워크플로우.
