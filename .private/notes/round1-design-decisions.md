# Round 1 — 1단계 설계 결정 & 과정 기록

> 작업용 기록(`.private/notes/`). 채점용 "설계 결정 문서"(왜/근거)는 사용자가 별도 작성.
> 여기엔 *무엇을 정했나 + 어떤 과정으로 도달했나*만 남긴다.

## 0. 목표/범위

- Round 1 · 1단계 = **System Prompt + Structured Output**.
- 산출 핵심: `SupportResponse` 설계 + `SupportController` 구현 + 시나리오 3종 검증.
- QUEST: 코드보다 **설계 결정의 근거**가 채점 비중 큼 → 근거 글은 사용자 몫.

## 1. System Prompt 결정 (완료, `BaedalPrompt.java` 반영됨)

- **B안 채택**: 감정 인지 단계 명시 + 구체적 되묻기 예시("주문번호를 알려주시겠어요?") + "결제/환불 금액은 시스템 조회값만".
- 발제(README) 공식 레퍼런스 프롬프트와 **정확히 일치** → 과정 표준과 부합.

## 2. 설계 기준이 잡혀온 과정 (사용자 주도, AI는 스파링)

1. 사람 상담사 흐름 3단계: **확인("알아보고 연락")→ 상황 파악·전달 → 다음 프로세스 인계**.
2. "잘하는 상담원 7기준"을 렌즈로 사용(감정먼저 / 정확성·근거 / 기대치 / 명확한 다음단계 / 비례적 구제 / 일관성 / 한계→에스컬레이션).
3. **prompt vs 응답값 기준(사용자 정의)**:
   - 프롬프트 = 내용에 항상 포함·AI가 놓치지 않게 하는 **불변 지시/정책**.
   - 응답값 = **컨텍스트의 상태(state)**. 실제 판정 테스트 = **"다음 인자로 쓰일 수 있는가?"** (이전 2층(state→응답값 / 서버가 쓰나)을 1층으로 통합).
4. 전제 정정: *"DB 없음"은 발제에 명시된 적 없음.* 배달 서비스·시스템은 **이미 존재**, Round 1은 Tool 미연결(= Round 2). → 미래지향 필드도 정당하되 1단계는 최소형.
5. 리서치 기반 시나리오 5종으로 현 구조의 긴장 확인(보상/위생·안전 카테고리 공백, 지연+취소 다중분류, 누락 ORDER/DELIVERY 모호 등).

## 3. 후보 판정 (사용자 번호 그대로, "다음 인자" 테스트 결과)

| # | 항목(사용자 표현) | 판정 | 1단계 |
|---|---|---|---|
| 1 | 사용자 감정 | state(응답값), 말투도 구동 → 2순위 | **제외(보류)** |
| 2 | 판단인지 진실인지(추정/확인) | 강한 인자 | **채택** |
| 3 | 2번 근거 위치 | 핸드오프 시 참조 → 인자. 객체지향이나 1단계 최소형 | **채택(얇게)** |
| 4 | 기대치 | 불변 정책 → 프롬프트 | 필드 아님 |
| 5 | 고객이 할 행동 | nextAction 분해의 일부 | **채택** |
| 6 | 조치의 카테고리 | 강한 인자지만 2순위 | **제외(보류)** |
| 7 | 휴먼 핸드오프 | 가장 강한 인자(라우팅 그 자체) | **채택** |
| - | nextAction | 너무 모호 → **분해**: 상담/시스템 측 다음 단계 + 고객이 할 행동(#5) | 적용 |

## 4. 1단계 최종 결정

**`SupportResponse` 목표 형태** (필드명/타입은 코드 제시 때 확정):

- 유지: `summary:String`, `category:Category`, `urgency:Urgency`, `neededInfo:List<String>`
- nextAction 분해 → 상담/시스템 측 다음 단계 `String` + 고객이 할 행동 `String`(#5)
- 신규(1순위): 판단 성격(추정/확인, #2) / 근거 참조(#3, 최소형) / 휴먼 핸드오프(#7)

**그 외 확정**:
- `Category` enum **5개 유지**(ORDER/DELIVERY/REFUND/PAYMENT/ETC). "왜 충분/공백" 근거 = 사용자 작성 예정.
- `SupportController` = **발제 예시(A): 요청마다 `builder.defaultSystem(...).build()...`**. (B 생성자 1회 빌드와 기능 동일, 효율만 차이 → 학습상 발제 일치 우선)
- 검증 환경: Ollama + `qwen2.5` + `./gradlew bootRun` **가능**.

## 5. 1단계에서 보류(Round 2+ / 추후)

- #1 감정, #6 조치 카테고리 (2순위)
- #3 근거 객체화·전방호환(Round1 얇게/Round2 풍부)
- "주체(과실 귀속)" — [금지]급 정책 결정으로 별도 판단
- 일관성 정량 검증(`categoryConsistency`) = 2단계
- nextAction 분해 필드의 flat/nested 세부

## 6. 검증 계획

- 코드 반영 후 시나리오 3종 호출:
  1. "주문번호 2024-1234 배달 어디쯤에 있어요?"
  2. "방금 시킨 주문 취소하고 싶어요. 환불은 얼마나 걸려요?"
  3. "라이더가 음식을 엎었다는데 보상 받을 수 있나요?"
- 각 응답 JSON 기록 → 시나리오별 category/urgency가 다르게 나오는지 확인.

## 7. 미완 (사용자 작성, 채점 핵심 — AI 작성 금지)

- [ ] 각 추가 필드의 **선택 근거**
- [ ] `Category` 5개로 **충분한 이유 / 보상·위생 공백 처리 논리**
- [ ] System Prompt [금지] 3개를 **왜 그 3개로** 했는가

## 8. 검증 실행 #1 (코드 반영 직후, 프롬프트 미수정 상태)

환경: Ollama `qwen2.5`, `./gradlew bootRun`, `POST /api/v1/support`. 전부 HTTP 200.

| 필드 | S1 "배달 어디쯤" | S2 "취소·환불 얼마나" | S3 "라이더가 음식 엎음 보상?" |
|---|---|---|---|
| category | DELIVERY | ORDER | DELIVERY |
| urgency | NORMAL | NORMAL | **null** |
| answerBasis | INFERRED | **null** | **null** |
| basisReference | "" | null | null |
| needsHumanHandoff | false | false | false |
| agentNextStep | (채움) | (채움) | (채움) |
| customerAction | "주문번호 확인 요청" | "취소 요청" | **null** |
| neededInfo | [] | ["주문번호"] | ["주문번호를 알려주시겠어요?"] |
| 응답시간 | 17.2s(콜드) | 5.1s | 5.0s |

관측 요약:
- Structured Output(`.entity`) 자체는 정상 동작, 시나리오별 분류 상이 → 1단계 코드 통과.
- 신규 필드(`answerBasis`/`basisReference`)·기존 `urgency`가 S2·S3에서 **null** = 비일관 충전.
- `needsHumanHandoff` 항상 false(보상 케이스 S3 포함) = 에스컬레이션 미발생.
- 분류 논쟁: 취소·환불→ORDER, 라이더 보상→DELIVERY (Category 적정성 근거 데이터).

→ 원인 분석 및 프롬프트 반영 방향은 사용자와 한 항목씩 검토 중(이 절은 추후 갱신).

## 9. 검증 실행 #2 — L1b(@JsonProperty required + @JsonPropertyDescription) 적용 후

조치: SupportResponse 11필드에 `@JsonProperty(required=true)` + 필드별 `@JsonPropertyDescription`(판정 규칙) 추가. `replyToCustomer`(고객 답변문)·`handoffReason` 신규. 프롬프트 B는 미수정(D2).

| 필드 | S1 | S2 | S3 |
|---|---|---|---|
| 11필드 충전 | 전부 | 전부 | 전부 |
| answerBasis | INFERRED | INFERRED | INFERRED |
| basisReference | 고객 진술 | 고객 진술 | 고객 진술 |
| category | DELIVERY | REFUND | DELIVERY |
| urgency | NORMAL | NORMAL | NORMAL |
| needsHumanHandoff | false | false | false |

결과:
- **null 문제 해소** (#1의 null 필드들이 전부 충전). L1b 유효.
- 필드 규칙 준수: answerBasis 항상 INFERRED, basisReference "고객 진술", replyToCustomer 존댓말 답변 생성.
- S2 분류 #1 `ORDER`→#2 `REFUND` 로 타이브레이크 규칙 적중.
- 미발화: needsHumanHandoff(3종 다 false) — 안전/CRITICAL 케이스 부재 탓일 수 있음, 시나리오4류 별도 검증 필요(QUEST 범위 밖).
- S3 보상건 `DELIVERY` 분류 = Category 적정성 채점 근거 데이터.
- 응답시간 12~26s(스키마 확대로 프롬프트 증가) → 4부 Observability 연계.

검증 #2b — 핸드오프 룰 (시나리오4류: 이물질·건강위협):
- 입력: "애가 먹던 음식에서 머리카락·벌레, 애가 토함, 당장 조치"
- 결과: `urgency=CRITICAL`, `needsHumanHandoff=true`, 공감형 존댓말 `replyToCustomer` 정상 → **핸드오프/긴급도 룰 적중**.
- 흠: `handoffReason`이 true인데 `""` — 모델이 사유 채움 하위규칙 미준수(보강은 채점 설계 판단).

→ 1단계 코드/구조 완료. 채점용 "왜" 문서(§7)는 사용자 작성.
