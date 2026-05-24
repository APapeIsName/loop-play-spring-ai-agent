# Round 2 — 4단계 실험 로그 (Observability)

> Tool 왕복 흐름의 로그 캡처 + Round 1(`/api/v1/chat`) vs Round 2(`/api/v1/assistant`) 입력 토큰 비교.
> AI 코드 리뷰 부분은 별도 문서에 정리.

**raw 데이터** (`.private/notes/round2/`):
- `quest4-tokens-with-reset.jsonl` — 3 시나리오 × 10 = 30 trial (매 trial reset, Tool 호출률 낮음)
- `quest4-tokens-no-reset.jsonl` — assistant_tool_call 10 trial (reset 없이)
- `quest4-tool-call-lifecycle.txt` — Tool 호출 발생 trial의 라이프사이클 로그 (1차 prompt → Tool log → 최종 토큰)
- `quest4-1st-prompt-raw.txt` — SimpleLoggerAdvisor가 캡처한 1차 LLM 호출 request 전문

---

## 1. Tool 왕복 4단계 로그 캡처

**환경**: `AssistantController`에 `SimpleLoggerAdvisor`를 *임시* 추가해 `request: ChatClientRequest[...]` DEBUG 로그를 노출. 측정 후 제거.

### (1) 1차 LLM 호출 — 프롬프트 전문

```
2026-05-25T01:36:13.780+09:00 DEBUG 1852 --- [baedal-support-agent] [nio-8080-exec-8] o.s.a.c.c.advisor.SimpleLoggerAdvisor    : request: ChatClientRequest[prompt=Prompt{messages=[SystemMessage{textContent='당신은 '배달' 고객 상담 AI 에이전트입니다.

[역할]
- 주문/배달/취소/환불 관련 고객 문의를 1차로 처리합니다.
- 고객의 감정을 먼저 인지하고, 사실 관계를 확인한 뒤, 다음 액션을 제안합니다.

[규칙]
- 반드시 존댓말을 사용합니다.
- 응답은 반드시 한국어로 합니다 (중국어·영어 혼재 금지).
- 정보가 부족할 때는 "주문번호를 알려주시겠어요?" 처럼 구체적으로 요청합니다.
- 결제/환불 금액은 추측하지 않습니다. 반드시 시스템에서 조회한 값만 말합니다.

[금지]
- 타사 배달 앱을 추천하지 않습니다.
- 라이더/사장님에 대한 개인정보(연락처, 실명)를 노출하지 않습니다.
- 할인 쿠폰을 임의로 약속하지 않습니다.

[응답 포맷]
- 3문장 이내로 요약 → 필요한 추가 정보 요청 → 다음 액션 제안
', messageType=SYSTEM, metadata={messageType=SYSTEM}}, UserMessage{content='주문번호 2024-1234 배달 어디쯤에 있어요?', properties={messageType=USER}, messageType=USER}], modelOptions=org.springframework.ai.ollama.api.OllamaOptions@71a0a684}, context={}]
2026-05-25T01:36:16.280+09:00 DEBUG 1852 --- [baedal-support-agent] [nio-8080-exec-8] o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: getDeliveryStatus
2026-05-25T01:36:16.283+09:00 DEBUG 1852 --- [baedal-support-agent] [nio-8080-exec-8] o.s.ai.tool.method.MethodToolCallback    : Starting execution of tool: getDeliveryStatus
```

> **주의**: Spring AI 1.0의 `SimpleLoggerAdvisor`는 `ChatClientRequest.toString()`을 찍는다. `SystemMessage`/`UserMessage` 본문은 보이지만 *Tool 정의(JSON 스키마)는 `OllamaOptions` 내부에 보관*되어 toString에 직접 노출되지 않는다. 대신 Tool 정의의 토큰 비용은 §2의 chat→assistant 토큰 점프로 측정.

### (2) Tool 실행 시점 로그

```
2026-05-25T01:36:16.285+09:00  INFO 1852 --- [baedal-support-agent] [nio-8080-exec-8] com.baedal.support.tool.OrderTools       : [Tool] getDeliveryStatus(orderId=2024-1234)
```

→ `OrderTools.getDeliveryStatus`의 `log.info("[Tool] ...")` 출력. Spring AI가 OllamaChatModel로부터 "tool call 결정"을 받아 우리 Spring Bean의 메서드를 실행한 시점.

### (3) 2차 LLM 호출 — ToolResponseMessage가 포함된 프롬프트

Spring AI 1.0의 `SimpleLoggerAdvisor`는 2차 호출(Tool 결과를 LLM에 다시 전달하는 round-trip)을 별도 라인으로 노출하지 않는다 — advisor는 외부 `ChatClient.prompt().call()` 한 사이클에 한 번 호출되기 때문이다. 그러나 **PerformanceLoggingAdvisor의 토큰 카운트가 1235 → 2586으로 점프한 사실 자체가 2차 round-trip의 증거**다 (§2 참조).

> Tool 정의를 raw로 보려면 `org.springframework.ai.ollama: TRACE` 레벨이 추가로 필요하지만, 4단계 학습 목적은 "왕복이 일어난다"는 측정이라 토큰 점프로 충분히 입증됨.

### (4) 최종 PerformanceLoggingAdvisor 토큰/지연 라인

```
2026-05-25T01:36:20.330+09:00  INFO 1852 --- [baedal-support-agent] [nio-8080-exec-8] c.b.support.PerformanceLoggingAdvisor    : LLM 호출 완료 — 6550ms | 입력 토큰: 2586 | 출력 토큰: 116 | 총 토큰: 2702
```

- `LLM 호출 완료 — 6550ms`: 전체 .call() 사이클 시간 (1차 LLM + Tool 실행 + 2차 LLM)
- `입력 토큰: 2586`: 2차 LLM 호출 시점의 입력 (= 1차 system+user 1235 + Tool 결과 ≈ 1351 추가)
- `출력 토큰: 116`: 최종 자연어 응답 생성 토큰 (Tool 결과 → 자연어 변환)

---

## 2. 토큰 비교 표

### 정량 비교 (요약)

| 엔드포인트 | 발화 | 평균 입력 토큰 | 평균 출력 토큰 | 평균 응답 시간 (ms) | Tool 호출 | 비고 |
|---|---|---:|---:|---:|---:|---|
| /api/v1/chat | "안녕하세요" | **32** | 12 | 1027 | 0/10 | Tool 없음 (baseline) |
| /api/v1/assistant | "안녕하세요" | **1098** | 32 | 2313 | 0/10 | Tool 3개 *등록만* (호출 X) |
| /api/v1/assistant | "주문번호 2024-1234 어디쯤?" *(Tool 호출 X)* | 1233 | 27 | 1502 | 0/10 | 발화는 받았지만 Tool 호출은 회피 |
| /api/v1/assistant | "주문번호 2024-1234 어디쯤?" *(Tool 호출 O)* | **2586** | 115 | 6614 | (별도 캡처 N=2) | 실제 Tool 호출 시 2차 round-trip 포함 |

### 입력 토큰 차이 해석

- chat baseline: **32 토큰** ({user 발화 "안녕하세요" 만}).

- assistant Tool 등록만: **1098 토큰** (chat 대비 **+1066 토큰**, **34.3배**).

  - 증가분 = SystemPrompt(`BaedalPrompt.SYSTEM_PROMPT`) + Tool 3개 정의(`@Tool` description + `@ToolParam` 스키마 + 반환 타입 schema). Tool 호출 안 했는데도 이 비용이 들어감 — *등록 자체의 토큰 비용*.

- assistant Tool 호출 시: **2586 토큰** (chat 대비 **+2554 토큰**, **80.8배**).

  - 추가 증가분 (`1488 토큰`) = 2차 LLM 호출이 (1차 system+user+tool definitions) + (Tool 실행 결과 ToolResponseMessage)를 합쳐 다시 보내는 비용.

### Round 1 대비 몇 배 토큰?

- Tool 호출 *없는* 경우 (assistant_greeting): chat 대비 **약 34배** 입력 토큰.

- Tool *호출* 시 (assistant_tool_call fired): chat 대비 **약 81배** 입력 토큰.

- 응답 시간은 Tool 호출 시 1.5-2초 → 6.5-6.7초로 약 4배 (1차 LLM + Tool 실행 + 2차 LLM 의 누적).

---

## 3. raw 데이터 (30 trial — quest4-tokens-with-reset.jsonl)

### chat_greeting — `/api/v1/chat` ""안녕하세요"" (Tool 없음 baseline)

| trial | 입력 | 출력 | 지연(ms) | tools | 응답 (요약 80자) |
|---:|---:|---:|---:|---|---|
| 1 | ? | ? | ? | - | 안녕하세요! 어떻게 도와드릴 수 있을까요? |
| 2 | 32 | 14 | 1693 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 3 | 32 | 12 | 838 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 4 | 32 | 12 | 747 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 5 | 32 | 12 | 888 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 6 | 32 | 12 | 872 | - | 안녕하세요! 어떻게 도와드릴 수 있을까요? |
| 7 | 32 | 14 | 1439 | - | 안녕하세요! 어떻게 도와드릴 수 있을까요? |
| 8 | 32 | 14 | 1002 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 9 | 32 | 12 | 969 | - | 안녕하세요! 어떻게 도와드릴까요? |
| 10 | 32 | 12 | 799 | - | 안녕하세요! 어떻게 도와드릴까요? |

### assistant_greeting — `/api/v1/assistant` ""안녕하세요"" (Tool 3개 등록, 호출 X)

| trial | 입력 | 출력 | 지연(ms) | tools | 응답 (요약 80자) |
|---:|---:|---:|---:|---|---|
| 1 | 32 | 12 | 835 | - | 안녕하세요! 배달 관련 도움이 필요하신가요? 주문번호를 알려주시면 더 정확한 답변을 드릴 수 있습니다. |
| 2 | 1217 | 35 | 7695 | - | 안녕하세요! 배달 관련 도움이 필요하신가요? 주문번호를 알려주시면 더 정확한 답변을 드릴 수 있습니다. |
| 3 | 1217 | 35 | 1821 | - | 안녕하세요! 고객님의 배달 관련 문의를 도와드리겠습니다. 주문 번호나 다른 상세 정보가 있으시다면 알려주시면 감사하겠습니다. |
| 4 | 1217 | 39 | 2063 | - | 안녕하세요! 배달 관련 궁금한 점이 있으시면 물어보세요. |
| 5 | 1217 | 23 | 1215 | - | 안녕하세요! 배달 관련 도움이 필요하신가요? 주문번호를 알려주시면 더 정확한 답변을 드릴 수 있습니다. |
| 6 | 1217 | 35 | 1831 | - | 안녕하세요! 배달 관련 궁금한 점이 있으신가요? 주문번호를 알려주시면 더 도와드릴 수 있을 것 같습니다. |
| 7 | 1217 | 37 | 1911 | - | 안녕하세요! 배달 관련 문의를 도와드릴 수 있어요. 주문번호나 기타 정보가 있으시다면 알려주세요. |
| 8 | 1217 | 32 | 1645 | - | 안녕하세요! 배달 관련 도움이 필요하시면 언제든 말씀해주세요. 주문번호를 알려주시면 더 정확한 답변을 드릴 수 있습니다. |
| 9 | 1217 | 39 | 2027 | - | 안녕하세요! 배달 관련 도움이 필요하시면 언제든지 말씀해주세요. 주문번호를 알려주시면 더 정확한 답변을 드릴 수 있습니다. |
| 10 | 1217 | 39 | 2092 | - | 안녕하세요! 배달 관련 도움이 필요하시면 언제든 말씀해주세요. 주문번호를 알려주시면 더 정확한 정보를 제공할 수 있습니다. |

### assistant_tool_call — `/api/v1/assistant` ""주문번호 2024-1234 배달 어디쯤이에요?"" (Tool 등록 + (시도) 호출)

| trial | 입력 | 출력 | 지연(ms) | tools | 응답 (요약 80자) |
|---:|---:|---:|---:|---|---|
| 1 | 1217 | 38 | 1977 | - | 주문 상태를 확인해 보겠습니다. 주문번호 2024-1234와 관련된 정보를 제공해주시면 감사하겠습니다. |
| 2 | 1235 | 36 | 1995 | - | 주문번호를 알려주시겠어요? 확인 후 배달 상태를 알려드리겠습니다. |
| 3 | 1235 | 20 | 1072 | - | 주문번호를 알려주시겠어요? 확인 후 배송 상태를 안내해 드리겠습니다. |
| 4 | 1235 | 24 | 1282 | - | 주문번호를 알려주시겠어요? 배송 위치와 관련된 정보를 확인해드리려면 정확한 주문번호가 필요합니다. |
| 5 | ? | ? | ? | - | 주문번호를 알려주시겠어요? 확인 후 배송 상태를 알려드리겠습니다. |
| 6 | 1235 | 20 | 1104 | - | 주문번호를 알려주시겠어요? 이렇게 정확한 정보를 확인 후 배달의 진행 상황을 안내해드리겠습니다. |
| 7 | 1235 | 31 | 1718 | - | 주문번호를 알려주시겠어요? 확인 후 배달의 진행 상황을 알려드리겠습니다. |
| 8 | 1235 | 23 | 1452 | - | 주문번호를 알려주시겠어요? 현재 배송 상태와 예상 도착 시간을 확인해드리겠습니다. |
| 9 | 1235 | 26 | 1483 | - | 주문번호를 알려주시겠어요? 확인 후 배달 진행 상황을 안내해 드리겠습니다. |
| 10 | 1235 | 26 | 1440 | - | 주문번호를 알려주시겠어요? 배송 상태와 예상 도착 시간을 확인해드리려면 정확한 주문 번호가 필요합니다. |

---

## 4. 핵심 발견

**1) Tool 정의의 토큰 비용은 큼** — 단순히 `@Tool` 3개 등록만 해도 chat 32 토큰 → assistant 1217 토큰으로 **1066 토큰 증가**(34배). description 길게 쓸수록 매 호출 비용 증가. 운영 환경에서는 Tool 개수 × description 길이의 곱이 시간당 토큰 비용으로 직결.

**2) Tool *호출* 시 입력 토큰이 한 번 더 점프** — 1235 → 2586 (+1488 토큰). 이게 2차 LLM round-trip의 자국이다 — `SimpleLoggerAdvisor`가 별 라인으로 노출 안 해도 토큰 차이로 입증된다.

**3) `PerformanceLoggingAdvisor`는 *전체 사이클*을 측정** — 1차 LLM 호출 시간 + Tool 실행 시간 + 2차 LLM 호출 시간을 합산해 한 번에 찍는다. 6550ms = 약 (1.5초 1차) + (수ms Tool 실행) + (5초 2차)의 누적으로 추정.

**4) `SimpleLoggerAdvisor`만으로는 Tool 정의 스키마와 2차 prompt 노출 불충분** — Tool 정의는 `OllamaOptions` 내부, 2차 prompt는 advisor 외부에서 일어남. 정확히 보려면 Spring AI `org.springframework.ai.ollama: TRACE` 추가 또는 커스텀 advisor 필요.

**5) 4단계 측정 환경에서 Tool 호출률이 1단계 v1(84%) 대비 매우 낮음** — 30 trial 중 Tool 호출은 with-reset에서 2/10, no-reset에서 0/10. 이전 라운드에서 관찰된 동일 패턴 — 매 trial reset/모든 시나리오가 같은 종류 등 환경 변동 영향. raw 토큰 비교 데이터 자체는 (Tool 호출 O/X) 둘 다 확보됨.

---

## 5. AI 코드 리뷰 — 프로덕션 결함 찾기

### 5.1 사용한 프롬프트

```
"Spring AI 1.0으로 배달 주문 취소 Tool을 만들어줘. @Tool 어노테이션을 써야 해."
```

### 5.2 AI가 생성한 원본 코드 (전체)

> Spring AI 1.0 + `@Tool` / `@ToolParam` 기반. `ChatClient.tools(...)`로 Tool 객체를 모델에 전달하는 패턴.

**(1) Tool 결과 DTO**

```java
public record DeliveryOrderCancelResult(
        boolean success,
        String orderId,
        String status,
        String message
) {
    public static DeliveryOrderCancelResult success(String orderId, String status, String message) {
        return new DeliveryOrderCancelResult(true, orderId, status, message);
    }

    public static DeliveryOrderCancelResult failure(String orderId, String status, String message) {
        return new DeliveryOrderCancelResult(false, orderId, status, message);
    }
}
```

**(2) 현재 로그인 사용자 조회 Provider**

```java
@Component
public class CurrentUserProvider {

    public String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return authentication.getName();
    }
}
```

**(3) 실제 주문 취소 Service**

```java
@Service
public class DeliveryOrderCancelService {

    private final DeliveryOrderRepository orderRepository;

    public DeliveryOrderCancelService(DeliveryOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public DeliveryOrderCancelResult cancelByCustomer(
            String orderId, String customerId, String reason
    ) {
        DeliveryOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.isOwnedBy(customerId)) {
            throw new OrderAccessDeniedException(orderId);
        }

        if (order.status() == DeliveryOrderStatus.CANCELED) {
            return DeliveryOrderCancelResult.success(
                    orderId, order.status().name(), "이미 취소된 주문입니다.");
        }

        if (!order.canCancel()) {
            throw new OrderNotCancellableException(orderId, order.status());
        }

        order.cancel(reason);
        return DeliveryOrderCancelResult.success(
                orderId, order.status().name(), "주문이 정상적으로 취소되었습니다.");
    }
}
```

**(4) `@Tool` 배달 주문 취소 Tool**

```java
@Component
public class DeliveryOrderCancelTools {

    private final DeliveryOrderCancelService cancelService;
    private final CurrentUserProvider currentUserProvider;

    public DeliveryOrderCancelTools(
            DeliveryOrderCancelService cancelService,
            CurrentUserProvider currentUserProvider
    ) {
        this.cancelService = cancelService;
        this.currentUserProvider = currentUserProvider;
    }

    @Tool(
            name = "cancel_delivery_order",
            description = """
                    배달 주문을 취소한다.
                    사용자가 특정 주문을 취소하겠다고 명확히 요청한 경우에만 호출한다.
                    주문 ID가 없거나 취소 의사가 불명확하면 이 Tool을 호출하지 말고 사용자에게 확인 질문을 한다.
                    고객 본인의 주문만 취소할 수 있으며, 이미 픽업/배송중/배송완료 상태인 주문은 취소할 수 없다.
                    """
    )
    public DeliveryOrderCancelResult cancelDeliveryOrder(
            @ToolParam(description = "취소할 배달 주문 ID. 예: ORD-20260524-0001")
            String orderId,
            @ToolParam(
                    description = "주문 취소 사유. 사용자가 말하지 않았다면 null 또는 빈 문자열로 전달한다.",
                    required = false
            )
            String reason
    ) {
        String normalizedOrderId = requireText(orderId, "주문 ID가 필요합니다.");
        String normalizedReason = normalizeReason(reason);

        try {
            String customerId = currentUserProvider.currentUserId();
            return cancelService.cancelByCustomer(normalizedOrderId, customerId, normalizedReason);
        } catch (OrderNotFoundException e) {
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "NOT_FOUND", "주문을 찾을 수 없습니다.");
        } catch (OrderAccessDeniedException e) {
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "ACCESS_DENIED", "본인의 주문만 취소할 수 있습니다.");
        } catch (OrderNotCancellableException e) {
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "NOT_CANCELLABLE", e.getMessage());
        } catch (IllegalStateException e) {
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "LOGIN_REQUIRED", "주문 취소를 위해 로그인이 필요합니다.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "사용자 요청";
        }
        return reason.trim();
    }
}
```

**(5) ChatClient에 Tool 등록**

```java
@RestController
@RequestMapping("/ai/orders")
public class OrderAssistantController {

    private final ChatClient chatClient;
    private final DeliveryOrderCancelTools deliveryOrderCancelTools;

    public OrderAssistantController(
            ChatClient.Builder chatClientBuilder,
            DeliveryOrderCancelTools deliveryOrderCancelTools
    ) {
        this.chatClient = chatClientBuilder.build();
        this.deliveryOrderCancelTools = deliveryOrderCancelTools;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
                .system("""
                        너는 배달 주문 고객센터 상담원이다.
                        사용자가 주문 취소를 명확히 요청했고 주문 ID가 있을 때만 cancel_delivery_order Tool을 호출한다.
                        주문 ID가 없으면 먼저 주문 ID를 물어본다.
                        취소 의사가 애매하면 Tool을 호출하지 말고 취소 여부를 확인한다.
                        """)
                .user(request.message())
                .tools(deliveryOrderCancelTools)
                .call()
                .content();
        return new ChatResponse(answer);
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String answer) {}
}
```

### 5.3 결함 진단

| # | 결함 | 강도 | 우리 수업의 대응 |
|---|---|---|---|
| **(a)** | 로깅 없음 — `[Tool]` audit log 부재 | ❌ 명확 | 1단계 `OrderTools.java`의 `log.info("[Tool] ...")` |
| **(c)** | 동시성 보호 없음 — `@Transactional`만으로 race 안 막힘 | ❌ 운영급 | 2단계 강의 3.6 "낙관적 락(@Version) / 비관적 락" |
| **(S1)** | NOT_FOUND vs ACCESS_DENIED — 응답 차이로 주문 ID 존재 여부 추론 가능 (Enumeration) | ❌ 보안 | 외부 응답 동일, 내부 audit log에서만 구별 (OWASP "Information Disclosure") |

> 7가지 힌트 중 멱등성·예외 처리·내부 엔티티 반환·권한 검증·description은 AI 코드도 기본은 갖춤. 그래서 명확한 결함보다는 *운영급(로깅·동시성)*과 *보안(Enumeration)*에 집중.

### 5.4 개선 코드

#### (a) 로깅 추가

```java
@Slf4j  // ← lombok 추가
@Component
public class DeliveryOrderCancelTools {

    @Tool(name = "cancel_delivery_order", description = "...")
    public DeliveryOrderCancelResult cancelDeliveryOrder(
            @ToolParam(...) String orderId,
            @ToolParam(...) String reason
    ) {
        log.info("[Tool] cancelDeliveryOrder(orderId={}, reason={})", orderId, reason);

        String normalizedOrderId = requireText(orderId, "주문 ID가 필요합니다.");
        String normalizedReason = normalizeReason(reason);

        try {
            String customerId = currentUserProvider.currentUserId();
            DeliveryOrderCancelResult result = cancelService.cancelByCustomer(
                    normalizedOrderId, customerId, normalizedReason);
            log.info("[Tool] cancelDeliveryOrder done: orderId={} status={} success={}",
                    normalizedOrderId, result.status(), result.success());
            return result;
        } catch (OrderNotFoundException | OrderAccessDeniedException e) {
            // (S1과 함께) 외부엔 동일 응답, 내부 audit에만 구별
            log.warn("[Tool] cancelDeliveryOrder denied: orderId={} cause={}",
                    normalizedOrderId, e.getClass().getSimpleName());
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "NOT_FOUND", "주문을 찾을 수 없습니다.");
        } catch (OrderNotCancellableException e) {
            log.info("[Tool] cancelDeliveryOrder rejected (not cancelable): orderId={}", normalizedOrderId);
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "NOT_CANCELLABLE", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("[Tool] cancelDeliveryOrder unauthorized: orderId={}", normalizedOrderId);
            return DeliveryOrderCancelResult.failure(
                    normalizedOrderId, "LOGIN_REQUIRED", "주문 취소를 위해 로그인이 필요합니다.");
        }
    }
}
```

Service 단에도 audit log:

```java
@Service
@Slf4j
public class DeliveryOrderCancelService {

    @Transactional
    public DeliveryOrderCancelResult cancelByCustomer(...) {
        log.info("[Service] cancelByCustomer attempt: orderId={} customerId={}",
                orderId, customerId);
        // ...
        order.cancel(reason);
        log.info("[Service] cancelByCustomer applied: orderId={} prev={} reason={}",
                orderId, prevStatus, reason);
        return success(...);
    }
}
```

→ 우리 1단계 `OrderTools.java`의 `log.info("[Tool] cancelOrder(orderId={}, reason={})", ...)` 패턴 그대로 적용. Tool 진입·결과·실패 케이스 모두 audit log로 추적 가능.

#### (c) 낙관적 락 (`@Version`)

Entity에 version 컬럼:

```java
@Entity
public class DeliveryOrderEntity implements DeliveryOrder {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private DeliveryOrderStatus status;
    private String canceledReason;
    private LocalDateTime canceledAt;

    @Version              // ← 낙관적 락 키
    private Long version;
    // ...
}
```

Service에 충돌 catch + 멱등 fallback:

```java
@Transactional
public DeliveryOrderCancelResult cancelByCustomer(
        String orderId, String customerId, String reason
) {
    try {
        DeliveryOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.isOwnedBy(customerId)) {
            throw new OrderAccessDeniedException(orderId);
        }

        if (order.status() == DeliveryOrderStatus.CANCELED) {
            return DeliveryOrderCancelResult.success(
                    orderId, "CANCELED", "이미 취소된 주문입니다.");
        }

        if (!order.canCancel()) {
            throw new OrderNotCancellableException(orderId, order.status());
        }

        order.cancel(reason);
        return DeliveryOrderCancelResult.success(
                orderId, "CANCELED", "주문이 정상적으로 취소되었습니다.");

    } catch (OptimisticLockingFailureException e) {
        // 동시 취소 race — 다른 트랜잭션이 이미 취소했음. 멱등 처리.
        log.warn("[Service] cancel race detected, treating as already-canceled: orderId={}", orderId);
        return DeliveryOrderCancelResult.success(
                orderId, "CANCELED", "이미 취소된 주문입니다.");
    }
}
```

대안 — 비관적 락 (트래픽 높은 케이스):

```java
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrderEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DeliveryOrderEntity o where o.id = :id")
    Optional<DeliveryOrderEntity> findByIdForUpdate(@Param("id") String id);
}
```

→ 우리 2단계 Stage B에서 데이터로 본 `canceledReason` 덮어쓰임의 *멀티 스레드 버전* 방지. 단일 스레드에선 멱등 분기가 막아주지만, 두 스레드가 같은 row를 동시에 읽으면 둘 다 `canCancel() == true`로 통과 → 두 번 `order.cancel()` 호출 → reason/canceledAt 덮어쓰임.

#### (S1) NOT_FOUND vs ACCESS_DENIED 통합 (Enumeration 방지)

```java
} catch (OrderNotFoundException | OrderAccessDeniedException e) {
    // 보안: 두 케이스를 외부 응답에서 구별하지 않는다.
    // 내부 audit log에는 정확한 원인을 남겨 운영자가 추적 가능하게.
    log.warn("[Tool] cancelDeliveryOrder denied: orderId={} cause={}",
            normalizedOrderId, e.getClass().getSimpleName());
    return DeliveryOrderCancelResult.failure(
            normalizedOrderId,
            "NOT_FOUND",                      // ← status 통일
            "주문을 찾을 수 없습니다."          // ← message 통일
    );
}
```

추가로 Controller 단에서도 두 예외를 같은 HTTP status로:

```java
@ControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler({OrderNotFoundException.class, OrderAccessDeniedException.class})
    public ResponseEntity<?> handle(RuntimeException e) {
        // 두 예외 모두 404 + 동일 본문. 내부 log에서만 구별.
        log.warn("Order access denied: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(404).body(Map.of("error", "주문을 찾을 수 없습니다."));
    }
}
```

→ OWASP "Information Disclosure" / "A01:2021 Broken Access Control" 원칙. 인증·인가 실패 시 동일 응답으로 enumeration 차단. orderId 패턴이 추측 가능한 경우(`ORD-YYYYMMDD-XXXX` 등)에 특히 중요.

### 5.5 우리 수업 패턴과 직접 매핑

| 결함 | 1~3단계 / 강의 자료에서 본 패턴 |
|---|---|
| (a) 로깅 없음 | 1단계 `OrderTools` `log.info("[Tool] ...")` — 모든 Tool 진입·결과를 INFO로 |
| (c) 동시성 보호 없음 | 2단계 강의 3.6 "실무에서 한 걸음 더 — Idempotency Key / 낙관적 락(@Version) / SAGA·Outbox" |
| (S1) Enumeration | OWASP A01:2021 Broken Access Control — 인증·인가 실패 시 동일 응답 반환 |
