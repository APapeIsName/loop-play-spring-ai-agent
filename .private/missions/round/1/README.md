# Round 1 — Spring AI 기초와 배달 상담 에이전트 설계

> 📝
이 페이지는 Round 1의 **개요와 강의 본문**을 한 곳에 모은 문서입니다.
>

---

## 이번 라운드에 배우는 것

6라운드 과정이 끝나면 **배달 고객 상담 AI 에이전트**가 완성됩니다. Round 1은 그 토대입니다.

- Spring AI를 Ollama(Qwen)에 연결하고 첫 API를 띄운다
- `ChatClient` / `Advisor` / `Tool` / `VectorStore` 의 역할을 한 마디로 설명할 수 있게 된다
- “프롬프트를 감이 아닌 데이터로 판단하는” 사고방식을 처음 경험한다

> 🎯
이번 라운드의 한 줄 메시지: **“LLM은 판단자, 실행은 우리 서버가 한다.”**
판단과 실행의 경계를 긋는 감각이 6라운드 과정의 전부라고 해도 됩니다.
>

---

## 학습 목표

이번 라운드가 끝나면 다음을 할 수 있습니다.

- [ ]  Spring AI 프로젝트를 세팅하고 Ollama(Qwen)와 연결된 첫 API를 띄울 수 있다
- [ ]  `ChatModel` / `ChatClient` / `Advisor` / `Tool` / `VectorStore` 의 역할을 자기 언어로 설명할 수 있다
- [ ]  배달 상담 도메인에 맞는 **System Prompt**(역할/규칙/금지/포맷)를 설계할 수 있다
- [ ]  `ChatClient.entity()` 로 **Structured Output**(DTO)을 받을 수 있다
- [ ]  `Prompt Lab`으로 동일 시나리오를 반복 호출하고 일관성을 정량 비교할 수 있다
- [ ]  `.stream()` 으로 SSE Streaming 응답을 만들 수 있다
- [ ]  `CallAdvisor` 로 응답 시간/토큰 사용량을 로깅할 수 있다

---

## 사전 준비 체크리스트

> ⚠️
설치 시간이 길어 수업 중에 하면 뒤처집니다. 수업 **전에** 마쳐 주세요.
>
- [ ]  JDK 17 이상 설치
- [ ]  IntelliJ IDEA (Community 가능), Git 설치
- [ ]  Postman 또는 `httpie` 준비
- [ ]  Ollama 설치 + Qwen 모델 다운로드

```bash
# macOS — Homebrew 권장
brew install ollama
brew services start ollama   # 또는 `ollama serve`

# Linux: curl -fsSL https://ollama.com/install.sh | sh
# Windows: https://ollama.com/download/windows 인스톨러

# Qwen 모델 다운로드 (약 4.7GB, RAM 8GB+ 권장)
ollama pull qwen2.5

# 동작 확인
ollama list                  # qwen2.5 보이면 OK
ollama run qwen2.5 "안녕"     # 응답 확인 후 Ctrl+D
```

> 💡
Round 4 RAG에서는 임베딩 모델도 필요합니다. 지금 미리 받아두면 그 라운드가 매끄럽습니다.
>
>
> ```bash
> ollama pull nomic-embed-text   # 약 274MB
> ```
>

---

## 1부. Spring AI 이해

### 1.1 왜 Spring AI인가

우리가 만들 것은 “챗봇”이 아니라 **에이전트(Agent)** 입니다. 두 개의 차이를 먼저 분리해둡시다.

| 구분 | 챗봇 (Chatbot) | 에이전트 (Agent) |
| --- | --- | --- |
| 목적 | 질문에 답한다 | 문제를 해결한다 |
| LLM 역할 | 텍스트 생성 | **판단 + 도구 호출** |
| 외부 시스템 | 거의 연결하지 않음 | 주문/배송/결제 시스템을 읽고 쓴다 |
| 실패 처리 | 모른다고 답함 | Fallback/재시도/휴먼 핸드오프 |
| 대화 맥락 | 단발성 | 세션 단위 메모리 + 장기 기억 |

> 🎯
LLM은 “판단자”이고, 실제 실행은 **우리 서버가 한다**.
판단과 실행의 경계선을 잘 긋는 것이 이번 과정의 전부라고 해도 좋습니다.
>

### 1.2 Spring AI의 핵심 컴포넌트

| 컴포넌트 | 역할 | 비유 |
| --- | --- | --- |
| `ChatModel` | LLM과의 저수준 통신 (OpenAI, Anthropic, Ollama …) | JDBC Driver |
| `ChatClient` | 고수준 Fluent API. 대부분의 코드에서 이걸 쓴다 | JdbcTemplate |
| `Advisor` | 요청/응답 파이프라인 훅 (로깅, 메모리, RAG 주입) | Spring Interceptor |
| `Tool` (`@Tool`) | LLM이 호출할 수 있는 우리 코드 메서드 | RPC 엔드포인트 |
| `VectorStore` | 임베딩 저장소 (PgVector, Redis, Chroma …) | 검색 인덱스 |
| `EmbeddingModel` | 텍스트를 벡터로 변환 | `hashCode()` 의 의미론적 버전 |

### 1.3 이번 과정의 최종 모습

최종 프로젝트에서는 아래와 같은 흐름을 만듭니다. Round 1은 이 흐름의 **맨 위 박스(`ChatClient` 세팅)** 까지만 도달합니다.

```
사용자 문의
   │
   ▼
[ChatClient] ──▶ [System Prompt + Memory Advisor]
                         │
                         ▼
                 [RAG Advisor (FAQ 검색)]
                         │
                         ▼
                 [Tool Calling]
                  ├─ getOrderDetail(orderId)
                  ├─ getDeliveryStatus(orderId)
                  └─ cancelOrder(orderId, reason)
                         │
                         ▼
                 [Guardrail]
                         │
                         ▼
                  Structured Response
```

---

## 프로젝트 세팅

### 의존성 (`build.gradle`)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.baedal'
version = '0.0.1'
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories { mavenCentral() }

ext { set('springAiVersion', '1.0.0') }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}"
    }
}
```

> 💡
**왜 `starter-model-ollama`?** 이 과정은 Ollama로 로컬 실행합니다. API Key 없이 바로 시작할 수 있고, Round 2 Tool Calling부터 같은 스타터를 계속 씁니다.
>

### 설정 (`application.yml`)

```yaml
spring:
	application:
	name: baedal-support-agent
ai:
	ollama:
		base-url: http://localhost:11434
chat:
	model: qwen2.5
	options:
		temperature:0.3

logging:
	level:
		org.springframework.ai: DEBUG
```

> 💡
**Ollama 로컬 실행**: API Key가 필요 없습니다. `temperature: 0.3` — 상담 도메인은 창의성보다 **일관성**이 중요합니다.
>

### 첫 ChatController

```java
// ChatRequest.java — 공통 요청 DTO
public record ChatRequest(String message) {}
```

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatClientBuilder.build()
                .prompt()
                .user(request.message())
                .call()
                .content();
    }
}
```

```bash
# Ollama 실행 확인
ollama list  # qwen2.5 모델이 보여야 합니다

# 프로젝트 실행
./gradlew bootRun

# 테스트
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"안녕?"}'
```

> 🎯
**체크포인트 #1**: 여기까지 와야 환경 확인이 끝난 겁니다. 응답이 오지 않는다면 Ollama가 실행 중인지(`ollama list`), `base-url`이 맞는지부터 확인하세요.
>

---

## 2부. System Prompt & Structured Output

### 2.1 System Prompt 설계 원칙

좋은 System Prompt는 **페르소나 / 규칙 / 금지 / 포맷** 네 가지를 명확히 구분합니다.

```java
public final class BaedalPrompt {
    public static final String SYSTEM_PROMPT = """
        당신은 '배달' 고객 상담 AI 에이전트입니다.

        [역할]
        - 주문/배달/취소/환불 관련 고객 문의를 1차로 처리합니다.
        - 고객의 감정을 먼저 인지하고, 사실 관계를 확인한 뒤, 다음 액션을 제안합니다.

        [규칙]
        - 반드시 존댓말을 사용합니다.
        - 정보가 부족할 때는 "주문번호를 알려주시겠어요?" 처럼 구체적으로 요청합니다.
        - 결제/환불 금액은 추측하지 않습니다. 반드시 시스템에서 조회한 값만 말합니다.

        [금지]
        - 타사 배달 앱을 추천하지 않습니다.
        - 라이더/사장님에 대한 개인정보(연락처, 실명)를 노출하지 않습니다.
        - 할인 쿠폰을 임의로 약속하지 않습니다.

        [응답 포맷]
        - 3문장 이내로 요약 → 필요한 추가 정보 요청 → 다음 액션 제안
        """;
}
```

> 💡
프롬프트를 클래스 상수로 두면 (1) 버전 관리가 쉽고 (2) 테스트에서 assertion이 가능합니다.
>

### 2.2 Structured Output — 문자열 말고 DTO를 받자

텍스트 파싱은 부서지기 쉽습니다. Spring AI는 `ChatClient.entity()` 로 **DTO를 직접** 받습니다.

```java
public record SupportResponse(
        String summary,
        Category category,
        Urgency urgency,
        String nextAction,
        List<String> neededInfo
) {
    public enum Category { ORDER, DELIVERY, REFUND, PAYMENT, ETC }
    public enum Urgency  { LOW, NORMAL, HIGH, CRITICAL }
}
```

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient.Builder builder;

    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .build()
                .prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }

}
```

> 📝
위 코드에는 아직 Advisor가 없습니다. 5부(Observability)에서 `PerformanceLoggingAdvisor`를 만들어 `.defaultAdvisors()`로 등록합니다.
>

> 🎯
`.entity(Class)` 한 줄로 JSON 스키마가 자동 생성되어 LLM에 전달되고, 응답은 DTO로 역직렬화됩니다. **텍스트 파싱 금지**.
>

> 🎯
**체크포인트 #2**: `/api/v1/support`에 `"주문번호 2024-1234 배달 어디쯤에 있어요?"`를 보내서 JSON이 돌아오면 2부가 끝난 겁니다.
>

---

## 3부. Prompt Engineering — 감이 아닌 데이터로 판단하기

### 3.1 왜 정량 비교인가

System Prompt를 “느낌”으로 고치면 안 됩니다. 같은 시나리오를 여러 번 호출해서 **일관성**을 측정해야 합니다.

> 🎯
프롬프트 변경은 코드 변경이다. 변경 전/후를 데이터로 비교하고, 그 결과를 커밋 메시지에 남겨라.
>

### 3.2 실험 설계

하나의 시나리오를 **프롬프트 버전 × temperature × 반복 횟수**로 호출하여 비교합니다.

- PromptLabController 전체 코드 보기

    ```java
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/prompt-lab")
    public class PromptLabController {
    
        private final ChatClient.Builder builder;
    
        @PostMapping
        public PromptLabResult experiment(@RequestBody PromptLabRequest req) {
            var results = new java.util.ArrayList<SupportResponse>();
    
            var client = builder
                    .defaultSystem(req.systemPrompt())
                    .build();
    
            for (int i = 0; i < req.repeat(); i++) {
                var response = client.prompt()
                        .user(req.message())
                        .call()
                        .entity(SupportResponse.class);
                results.add(response);
            }
    
            return PromptLabResult.from(results);
        }
    
        public record PromptLabRequest(
                String systemPrompt,
                String message,
                int repeat  // 3~5회 권장
        ) {}
    
        public record PromptLabResult(
                int totalRuns,
                Map<String, Long> categoryCounts,
                Map<String, Long> urgencyCounts,
                double categoryConsistency  // 가장 많이 나온 category의 비율
        ) {
            public static PromptLabResult from(List<SupportResponse> results) {
                var catCounts = results.stream()
                        .collect(Collectors.groupingBy(
                                r -> r.category().name(), Collectors.counting()));
                var urgCounts = results.stream()
                        .collect(Collectors.groupingBy(
                                r -> r.urgency().name(), Collectors.counting()));
                long maxCat = catCounts.values().stream()
                        .mapToLong(Long::longValue).max().orElse(0);
    
                return new PromptLabResult(
                        results.size(), catCounts, urgCounts,
                        results.isEmpty() ? 0 : (double) maxCat / results.size()
                );
            }
        }
    }
    ```


### 3.3 실험 실행

```bash
# 기본 프롬프트로 5회 반복
curl -X POST http://localhost:8080/api/v1/prompt-lab \
  -H "Content-Type: application/json" \
  -d '{
    "systemPrompt": "당신은 배달 고객 상담 AI입니다. 존댓말을 사용하고, 3문장 이내로 답하세요.",
    "message": "주문번호 2024-1234 배달 어디쯤에 있어요?",
    "repeat": 5
  }'
```

**관찰 포인트:**

| 비교 축 | 무엇을 보는가 |
| --- | --- |
| **프롬프트 A vs B** | 구조화된 프롬프트(역할/규칙/금지/포맷)가 단순 프롬프트보다 일관성이 높은가? |
| **temperature 0.0 vs 0.3 vs 1.0** | `categoryConsistency`가 어떻게 변하는가? |
| **반복 횟수** | 3회로는 부족하고 5회면 충분한가? |

> 💡
`categoryConsistency`가 80% 이상이면 프로덕션에서 쓸 만합니다. 60% 이하면 프롬프트를 더 구체적으로 다듬어야 합니다.
>

---

## 4부. Streaming — 체감 속도를 바꾸는 한 줄

### 4.1 왜 Streaming인가

LLM은 응답을 한 번에 생성하지 않습니다. 토큰 단위로 순차 생성하는데, `.call()`은 전체 생성이 끝날 때까지 기다립니다. `.stream()`은 토큰이 생성되는 즉시 클라이언트로 흘려보냅니다.

| 방식 | 첫 응답까지 | 사용자 체감 |
| --- | --- | --- |
| `.call()` | 전체 응답 완성 후 (2~5초) | “로딩 중…” |
| `.stream()` | 첫 토큰 생성 시 (~0.3초) | 글자가 타이핑되듯 나타남 |

> 🎯
상담 에이전트에서 Streaming은 선택이 아닙니다. 고객이 “응답 없음”으로 느끼는 임계 시간은 **3초**입니다.
>

### 4.2 StreamingChatController

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/stream")
public class StreamingChatController {

    private final ChatClient.Builder builder;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .build()
                .prompt()
                .user(req.message())
                .stream()
                .content();
    }
}
```

의존성 추가 (`build.gradle`):

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

> 💡
**왜 WebFlux?** `Flux<String>`을 반환하려면 리액티브 스택이 필요합니다. `starter-web`과 `starter-webflux`는 같이 쓸 수 있고, Streaming 엔드포인트에만 `Flux`를 사용합니다.
>

### 4.3 테스트

```bash
# Streaming 테스트 — 토큰이 한 글자씩 흘러오는 것을 확인
curl -N -X POST http://localhost:8080/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'
```

> 🎯
**체크포인트 #3**: 터미널에서 글자가 한 글자씩 타이핑되듯 나타나면 성공입니다. 한꺼번에 출력되면 Streaming이 아니라 Buffering되고 있는 겁니다.
>

---

## 5부. Observability — LLM에 무엇이 전달되는지 확인하기

### 5.1 왜 중요한가

LLM 기반 시스템에서 가장 흔한 디버깅 실수는 **“내가 보낸 프롬프트와 LLM이 받은 프롬프트가 다르다”**는 것을 모르는 겁니다. Spring AI는 System Prompt, User Message 외에도 Structured Output 스키마 등을 자동으로 프롬프트에 주입합니다.

### 5.2 로그 레벨 설정

`application.yml`에 이미 설정되어 있습니다:

```yaml
logging:
	level:
		org.springframework.ai: DEBUG
```

이 설정으로 **실제 LLM에 전달되는 프롬프트 전문**을 확인할 수 있습니다.

### 5.3 로그에서 확인할 것

`/api/v1/support`를 호출하면 콘솔에 다음이 출력됩니다:

```
DEBUG o.s.a.c.c.ChatClient  : Prompt: [SystemMessage: 당신은 '배달' 고객 상담 AI...]
DEBUG o.s.a.c.c.ChatClient  : Response: ChatResponse [...]
```

**확인 포인트:**

| 로그 항목 | 확인할 것 |
| --- | --- |
| **SystemMessage** | 내가 작성한 System Prompt가 그대로 전달되는가? |
| **UserMessage** | 사용자 입력 외에 추가된 내용이 있는가? (.entity() 시 JSON 스키마가 붙음) |
| **응답 메타데이터** | 사용된 토큰 수, 모델명, finish reason |

### 5.4 응답 시간 측정

간단한 로깅 Advisor를 만들어 호출 시간을 측정합니다:

- PerformanceLoggingAdvisor 전체 코드 보기

    ```java
    @Slf4j
    @Component
    public class PerformanceLoggingAdvisor implements CallAdvisor {
    
        @Override
        public String getName() {
            return "PerformanceLoggingAdvisor";
        }
    
        @Override
        public int getOrder() {
            // 체인 바깥쪽에서 LLM 왕복 시간을 측정하기 위해 큰 값을 준다.
            // 낮은 order가 먼저 실행되므로, 다른 Advisor(Memory/RAG 등)가 프롬프트를 조립한 뒤
            // 마지막에 Performance가 호출 시간을 집계하는 구조.
            return 100;
        }
    
        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            long start = System.currentTimeMillis();
            ChatClientResponse response = chain.nextCall(request);
            long elapsed = System.currentTimeMillis() - start;
    
            var chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                log.info("LLM 호출 완료 — {}ms | 입력 토큰: {} | 출력 토큰: {} | 총 토큰: {}",
                        elapsed,
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens());
            } else {
                log.info("LLM 호출 완료 — {}ms (metadata 없음)", elapsed);
            }
    
            return response;
        }
    }
    ```


> 📝
**Spring AI 1.0 GA API**: `CallAdvisor`의 시그니처는 `ChatClientResponse adviseCall(ChatClientRequest, CallAdvisorChain)`. 마일스톤 시절의 `AdvisedRequest/AdvisedResponse` 는 GA에서 교체되었다. 또한 응답 메타데이터는 `response.chatResponse()` 를 통해 접근하며, null 방어가 필요하다 (일부 Advisor 체인 중간에선 metadata가 없을 수 있음).
>

`@Component`로 빈 등록되어 있으므로, DI로 주입받아 ChatClient에 등록합니다:

```java
private final PerformanceLoggingAdvisor performanceAdvisor;

// ChatClient 빌드 시
builder
    .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
    .defaultAdvisors(performanceAdvisor)
    .build()
```

> 🎯
프로덕션에서는 모든 LLM 호출에 대해 **응답 시간, 토큰 수, 모델명**을 로깅해야 합니다. Round 1부터 이 습관을 들여두세요.
>

> 💡
같은 질문이라도 System Prompt가 길어지면 입력 토큰이 늘고, temperature가 높으면 출력 토큰이 늘어나는 경향을 확인할 수 있습니다.
>

---

## 다음 라운드 예고 — Round 2: Tool Calling 기초

다음 시간에는 드디어 **LLM이 우리 코드를 호출**하게 만듭니다.

- `@Tool` 어노테이션으로 `getOrderDetail(orderId)` 노출
- “판단은 LLM, 실행은 서버” 경계선 긋기
- Tool이 예외를 던질 때 에이전트가 어떻게 반응해야 하는지
- (선행 학습 권장) [Spring AI Tool Calling 문서](https://docs.spring.io/spring-ai/reference/api/tools.html) 한 번 읽어오기

---

> 💡
**질문하는 법**: 막히는 부분은 **“어떤 입력에서 어떤 출력이 나오기를 기대했는데 실제로 무엇이 나왔다”** 형식으로 정리해서 올리면 가장 빨리 풀립니다.
>