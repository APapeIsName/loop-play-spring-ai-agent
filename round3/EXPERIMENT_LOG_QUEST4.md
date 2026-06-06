# Round 3 — 4단계 실험 로그

> QUEST 4단계 — Observability + AI 코드 리뷰.
>
> **데이터 출처**: 1단계 100 trials + 2단계 300 turns + 2단계b 30턴 시퀀스
> **AI 코드 리뷰**: Workflow tool 3-agent pipeline (naive 생성 → 결함 분석 → 개선 코드)

---

## Part A — Observability (Memory가 프롬프트에 끼어드는 흔적)

### A.1 — 매 turn 입력 토큰 추세 (1·2·2b 종합)

| 측정 | 1턴 토큰 | 마지막 토큰 | 증가폭 | 배수 |
|---|---:|---:|---:|---:|
| **1단계** (2-turn 시나리오) | 1115 | T2 ~2400 | +1200 | 2.0× |
| **2단계 MAX=2** (10턴) | 1115 | 2679 | +1564 | 2.4× |
| **2단계 MAX=20** (10턴) | 1204 | 3763 | +2559 | 3.1× |
| **2단계 MAX=MAX** (10턴) | 1204 | 3851 | +2647 | 3.2× |
| **2단계b MAX=MAX** (30턴 누적) | 1115 | **4055** | +2940 | 3.6× |

### A.2 — 입력 토큰의 구성 분해 (바이트코드 + 측정 종합)

baseline 약 **1100 토큰**:
- System Prompt (BaedalPrompt): ~400 토큰
- Tool 스키마 (getDeliveryStatus + getOrderDetail + cancelOrder): ~600 토큰
- USER 첫 발화: ~100 토큰

T2 누적 약 +1500 토큰 (1단계 기준):
- **ToolMessage layer (Memory 외부)**: ~1500 토큰 — `MessageChatMemoryAdvisor`가 USER/ASSISTANT만 저장하지만 Spring AI ChatClient *내부 tool calling loop*가 ToolMessage를 다음 LLM 호출 prompt에 자동 포함. MAX_MESSAGES 영향 받지 않음.
- USER/ASSISTANT layer (Memory): ~100-1000 토큰 — MAX_MESSAGES에 따라 잘림. 2턴 시나리오에선 짧음, 10턴에선 ~1000, 30턴 누적에선 더 늘어남.

**`MessageWindowChatMemory` 바이트코드 검증** (2단계 발견 1):
- SystemMessage *무조건 유지* (자르기 대상 제외)
- USER/ASSISTANT 중 *오래된 것부터* `(size - maxMessages)` 개 잘라냄
- ToolMessage *애초에 add 안 됨* (Advisor.after()가 `response.getResults()`만 전달)

### A.3 — 응답 시간 turn별 추세 (2단계b MAX=MAX, 30턴, 10 repeat 평균)

| turn | ms | 의미 |
|---:|---:|---|
| 1 | 11334 | ollama 워밍업 + Tool 호출 (가장 오래) |
| 2 | 9548 | Tool 호출 (긴 응답) |
| 3 | 3721 | Memory 재사용, Tool 없음 |
| 5 | 5218 | Tool 호출 일부 |
| 10 | 6283 | 요약 (1차 누적) |
| 15 | 3394 | Memory 재사용 |
| 20 | 6683 | Tool 호출 |
| 25 | 3301 | Memory 재사용 |
| 30 | **9242** | 요약 (3차 누적, 30턴 컨텍스트 처리) |

→ **turn 30 요약이 turn 25보다 3배 느림** = 누적 컨텍스트가 LLM 처리에 *직접 영향*.

### A.4 — Memory가 프롬프트 조립 시점에 끼어드는 증거

발제 4.5 + 2단계 발견 1 + 2단계 발견 5 결합:

1. **1단계 SessionController 응답 = USER × 2 + ASSISTANT × 2 = 4 메시지** (모든 trial 일관)
2. **PerformanceLoggingAdvisor 입력 토큰 = 2400** (1단계 T2)
3. **4 메시지 × ~60 토큰 = ~240 토큰** ≠ 2400 — 차이 ~2100 토큰
4. **차이의 정체** = baseline (1100) + ToolMessage layer (~1000 — 첫 turn Tool 호출 결과가 다음 LLM 호출에 자동 포함됨)

**해석**: Memory Advisor의 *프롬프트 조립*은 *SessionController 외부의 ToolMessage layer*까지 합쳐서 LLM에 전달. SessionController API는 *영속 Memory*만 보여주고, Tool 호출 history는 *ChatClient 내부 transient layer*에 별도.

### A.5 — Round 2 → Round 3 진화 (가설 1·2 정량)

**가설 1** (Memory가 reason hallucination 잡음) — 1단계 발견 5:
- Round 2 scn 4: 9/9 (100%) 동일 example fallback (`"집 앞에 사람이 없어요"`)
- Round 3 scn 2: 2/9 (22%) example fallback, 7/9 (78%) 합리적 reason
- **78% 개선** (조건부)

**가설 2** (Memory가 응답-실재 분리 해결) — 1단계 발견 6:
- 사용자 통찰 *"Memory는 SOT 역할 불가"* 확정
- 다양한 형태로 재발: ETA 임의 변경 (trial 1·10), fresh state "찾을 수 없음" (scn2 trial 20), Tool 미호출 (scn2 trial 10), 1235 "이미 배달 완료" 환각 (2b max2)
- Memory + Prompt + LLM 자연어 일관성 *셋 다 필요*

---

## Part B — AI 코드 리뷰 (Workflow 결과)

> Workflow pipeline: Naive 코드 생성 → 결함 분석 → 개선 코드
> Run ID: `wf_c92b3bb0-a5f`, 3 agents, 83,598 subagent tokens, 203s
> 전체 결과: `/private/tmp/claude-501/.../wrqe0ji57.output`

### B.1 — Naive 코드 (AI가 생성한 fast prototype)

**ChatMemoryConfig.java** (의도된 naive):
```java
@Bean
public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(100) // 한 세션당 100개 메시지 무제한 누적 가능
            .build();
}

@Bean
public ChatClient assistantChatClient(OllamaChatModel chatModel, ChatMemory chatMemory) {
    return ChatClient.builder(chatModel)
            .defaultSystem("너는 배달 주문을 도와주는 친절한 한국어 상담원이야...")
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
}
```

**AssistantController.java** (의도된 naive):
```java
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private long sessionCounter = 0L;  // ← 비원자적 카운터

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body,
                                    @RequestParam(required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionCounter++;
            sessionId = "session-" + sessionCounter;  // ← 예측 가능
        }
        String reply = chatClient.prompt()
                .user(body.get("message"))
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call().content();
        return Map.of("sessionId", sessionId, "reply", reply);
    }

    @GetMapping("/history")
    public Object history(@RequestParam String sessionId) {
        return chatMemory.get(sessionId);  // ← 인증 0
    }
}
```

### B.2 — 결함 분석 (AI agent가 발견한 8개)

| # | category | severity | 핵심 |
|---:|---|---|---|
| 1 | **동시성** | HIGH | `sessionCounter++` 비원자적 → 두 요청이 같은 sessionId 발급 → 메모리 버킷 섞임 |
| 2 | **세션 오염** | **CRITICAL** | `session-1` 같은 예측 가능 ID → 추측만으로 남의 대화 침투 |
| 3 | **메모리 누수** | HIGH | TTL/세션 expiry 없음 → 한번 대화한 세션도 영구 잔류 → OOM |
| 4 | **재시작 시 소실** | HIGH | InMemory만 → 배포·OOM·점검 한 번에 모든 세션 증발 |
| 5 | **멀티 인스턴스 미고려** | **CRITICAL** | 인스턴스 로컬 Map + 인스턴스 로컬 카운터 → 로드밸런서 뒤 *모든 패턴* 깨짐 (대화 끊김 / ID 충돌 / sticky 의존) |
| 6 | **프라이버시** | HIGH | `/assistant/history` 무인증 → sessionId만 알면 PII 평문 조회 |
| 7 | **윈도우 미설정** | MEDIUM | `maxMessages(100)` = 약 50왕복 → 입력 토큰 선형 폭증 |
| 8 | **conversationId 하드코딩** | MEDIUM | defaultAdvisors에 메모리 박힘 + conversationId 누락 시 default fallback → 단일 버킷 합쳐짐 |

### B.3 — 개선 코드 (Round 3 패턴 적용)

전체 파일 4개 (`ChatMemoryConfig` / `AssistantChatClientConfig` / `AssistantController` / `SessionController`).

**핵심 변경 — `ChatMemoryConfig`**:
```java
private static final int MAX_MESSAGES = 20;  // 5~10턴 × 2 메시지 가정

@Bean
@Profile("!jdbc")  // jdbc 프로필이면 자동 구성된 JdbcChatMemoryRepository 사용
public ChatMemoryRepository chatMemoryRepository() {
    return new InMemoryChatMemoryRepository();
}

@Bean
public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
    return MessageChatMemoryAdvisor.builder(chatMemory)
            .order(10)  // PerformanceLogging보다 먼저
            .build();
}
```

**핵심 변경 — `AssistantController`**:
```java
private static final String SESSION_HEADER = "X-Session-Id";

@PostMapping
public ResponseEntity<Map<String, Object>> ask(@RequestBody ChatRequest req,
        @RequestHeader(value = SESSION_HEADER, required = false) String headerSessionId) {

    // 정책 A: 헤더 누락 시 UUID 발급 (응답 헤더로 돌려줌)
    // 정책 B: 더 엄격 — requireSessionId()로 400 Bad Request
    String sessionId = (headerSessionId == null || headerSessionId.isBlank())
            ? UUID.randomUUID().toString()
            : headerSessionId;

    String reply = assistantChatClient.prompt()
            .user(req.message())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .call().content();

    return ResponseEntity.ok()
            .header(SESSION_HEADER, sessionId)
            .body(Map.of("sessionId", sessionId, "reply", reply));
}
```

**핵심 변경 — `SessionController`** (PII 보호):
```java
@RequestMapping("/api/v1/admin/session")  // /history → /admin/session
public class SessionController {

    @Value("${app.session.admin-token:}")
    private String adminToken;

    private void requireAdmin(String token) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);  // 시크릿 미설정 = 엔드포인트 자체 숨김
        }
        if (token == null || !adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
    // 모든 endpoint에 requireAdmin(token) 가드
}
```

### B.4 — 결함 → Round 3 lesson 매핑

| 결함 | 적용한 Round 3 lesson |
|---|---|
| **동시성** | `sessionCounter++` 제거 → `UUID.randomUUID()` stateless ID 발급 |
| **세션 오염** | 예측 가능 `session-N` → 추측 불가 UUID + `defaultValue="default"` fallback 의도적 제거 |
| **메모리 누수** | `maxMessages(20)` + `@Profile("!jdbc")` 로 JDBC 영속화 옵션 → TTL 배치로 청소 가능 |
| **재시작 시 소실 + 멀티 인스턴스** | `JdbcChatMemoryRepository` (jdbc 프로필) — 공유 저장소 + 외부 UUID conversationId |
| **프라이버시** | `/assistant/history` → `/api/v1/admin/session` + `X-Admin-Token` 시크릿 가드 + 미설정 시 404로 숨김 |
| **윈도우 미설정** | `maxMessages(100)` → `20` (5~10턴 × 2 = 약 10왕복 기준) — 1단계 설계 결정과 일치 |
| **conversationId 하드코딩** | `ChatClient`를 `@Bean` (빌더 누적 회피) + conversationId 호출 시점 명시 주입 + fallback 거부 가드 |
| **Advisor 순서 (보너스)** | `MessageChatMemoryAdvisor.order(10)` — Performance보다 먼저 prompt 조립 |

### B.5 — AI 코드 리뷰 핵심 발견

1. **AI agent가 가장 critical로 짚은 두 결함은 *세션 오염*과 *멀티 인스턴스 미고려*** — 1단계 발제·QUEST의 보안 사고 시뮬레이션 라인과 일치.
2. **naive `defaultValue="default"`와 `session-N` 카운터의 조합이 IDOR-급**: 카운터로 발급된 sessionId를 *추측해서 보내면 남의 대화 입주* 가능.
3. **개선 코드가 *두 가지 대안*을 같이 제시** (서버 UUID 발급 vs 400 거부) — *환경에 따라 선택* 권장. DESIGN_DECISIONS Q4와 정합.
4. **AI가 윈도우 100을 *"약 50왕복"* 으로 환산 + 토큰 폭증 우려까지 명시** — 2단계 측정 결과와 일치 (MAX=20 → MAX=MAX는 10턴엔 동일하지만 *50턴 이상이면 폭증*).
5. **AI가 *Advisor 순서*를 8 카테고리에 없었지만 개선 코드 적용 lesson에 *보너스*로 추가**: order(10) — Performance보다 먼저. *바이트코드 검증 (Workflow 외부)*과 정합.

---

## Part C — 4단계 자가 점검

- [x] 10턴 시퀀스 입력 토큰 표 (2단계 결과 활용)
- [x] 1턴 vs 10턴 토큰 배수 계산 (MAX=20: 3.1×, MAX=MAX: 3.2×)
- [x] 입력 토큰 증가 원인 (ToolMessage layer + USER/ASSISTANT)
- [x] **Memory 포함된 2회차 프롬프트 *전문* 캡처** — `org.springframework.web.client=DEBUG` + 단발 2턴 호출. raw payload 보존: [`quest4-prompt-payload.txt`](quest4-prompt-payload.txt)

### Part A.6 — Memory 끼어든 prompt 전문 (TRACE 캡처)

`org.springframework.web.client=DEBUG`로 *RestClient outgoing request body* 캡처. T2 호출 시 ChatRequest:

```
Writing [ChatRequest[
  model=qwen2.5,
  messages=[
    Message[role=USER, content=2024-1234 어디쯤이에요?, images=null, toolCalls=null],          ← ⭐ Memory에서 끼어든 1턴 USER
    Message[role=ASSISTANT, content=주문번호를 알려주시겠어요? 현재 배송 상태와 위치를 확인해드리려면 정확한 주문 정보가 필요합니다.,
            images=null, toolCalls=null],                                                       ← ⭐ Memory에서 끼어든 1턴 ASSISTANT
    Message[role=SYSTEM, content=당신은 '배달' 고객 상담 AI 에이전트입니다. ...],
    Message[role=USER, content=그거 언제 도착해요?, ...]                                        ← 새 T2 발화
  ],
  tools=[Tool[getOrderDetail], Tool[getDeliveryStatus], Tool[cancelOrder]],
  options={temperature=0.3}
]
```

**T1 vs T2 메시지 수 변화**:
- T1: 2 messages (SYSTEM + USER)
- T2: **4 messages** (1턴 USER + 1턴 ASSISTANT + SYSTEM + 새 USER)

→ 발제 2.6 체크포인트 *"2회차 DEBUG 로그에서 프롬프트 길이가 1회차보다 길어졌는지 눈으로 확인"* **직접 증명 ✓**.

→ Memory가 *USER + ASSISTANT만 끼어듦* 확정 (발제 4.5 + 바이트코드 + raw payload 3중 검증). **ToolMessage 없음** (Memory 외부 layer).

자세한 raw + 3 HTTP 호출 + Ollama ChatRequest 구조: [`quest4-prompt-payload.txt`](quest4-prompt-payload.txt)
- [x] AI 코드 리뷰 — 원본 코드 + 결함 8개 + 개선 코드 + 매핑 ✓ (Workflow `wf_c92b3bb0-a5f`)

---

## 다음 — 2단계 b recovery + 종합 정리

QUEST 4단계 완료 후:
1. 2단계 b recovery (max2 + max20 재측정, ~55분)
2. 종합 — 5개 draft 정식 승격 + JOURNAL Round 3 entry
3. Round 2 PR 패턴으로 PR 작성
