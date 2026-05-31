# Round 3 — 설계 결정

> QUEST 1·2·3단계 자가 점검 "설계 결정 문서" 통합.
> 각 질문 끝의 **사용자 답 영역**은 본인 답으로 수정 필요.
> AI draft 기반 — 측정 데이터로 합리적 답 시안 제시. 본인 운영 가정 + 의견으로 최종 결정.


> QUEST 1단계 "설계 결정 문서" 4개 질문에 대한 측정 데이터 기반 답.
> AI가 draft 작성 → 사용자가 자기 답으로 수정·승인 → 정식 `DESIGN_DECISIONS.md` 승격.

---

## 1. `MAX_MESSAGES = 20` 선택 근거

**선택값**: 20

**근거 (계산)**:

배달 상담 평균 대화 패턴 가정:
- 고객 1턴 (USER) + 봇 1턴 (ASSISTANT) = 2 메시지 / 라운드
- 한 상담의 평균 라운드: 5~8 라운드 (정보 조회 + 후속 질문 + 액션 + 확인)
- 따라서 평균 메시지 수: 10~16 메시지

`MAX_MESSAGES = 20`은 **평균 상담 1건 + 25% 여유**:
- 평균 5~8 라운드를 *완전히* 포함
- 길게 늘어진 상담(예: 환불 협의)에서도 *직전 5라운드*는 항상 보존
- 너무 작으면(예: 2): 직전 1 라운드만 남아 *지시 대명사 해결 망가짐* (2단계에서 실험)
- 너무 크면(예: `Integer.MAX_VALUE`): 100턴 누적 시 입력 토큰 선형 증가 → 비용·지연 폭증

**측정 데이터로 본 검증**:
- Round 3 1단계 100 trials 모두 `MAX_MESSAGES = 20` 사용
- 모든 시나리오에서 Memory 메시지 수 4 msgs (USER×2 + ASSISTANT×2) — 2턴 시나리오라 윈도우 충분
- 윈도우 부족으로 인한 데이터 손실 0건

**한계 인정**:
- 5라운드 이상 상담의 *실제 분포*는 미측정. 평균 5~8 가정은 *추정*.
- 2단계에서 `MAX_MESSAGES = 2 / MAX` 양 옆 측정으로 선택 정당성 검증 예정.

> **사용자 답** (리서치 기반):
>
> AI draft의 *5~8 라운드 가정*은 외부 산업 자료로 재해석하면 *p75~p90 영역*에 해당. **MAX_MESSAGES = 20 유지**.
>
> **산업 데이터** (웹 리서치 종합):
> - 평균: **3-5 turn** (산업 통계, 마케팅 블로그 — 신뢰도 중간, 여러 출처 합의)
> - p90: ~5-6 turn (90%가 11 메시지 이내 해소)
> - p95: ~6-7 turn (13-15 메시지)
> - LLM 챗봇 만족도 — **7 turn에서 ↑** (arXiv 2404.17025, HCI peer-reviewed)
> - 멀티도메인 task-oriented: 평균 **13.7 turn** (MultiWOZ, arXiv 1810.00278) — 단일 CS보다 김
>
> **MAX = 20 정량 정당성**:
> 1. p95(13-15 메시지) × ~1.3배 = long-tail 흡수
> 2. 산업 평균(8-10 메시지) × ~2배 = 안전 마진
> 3. Round 3 측정으로 *추가 검증*: 1단계 100 trial Memory 4 msgs 일관 (윈도우 부족 데이터 손실 0건) + 2단계 b 30턴 토큰 *V자 sweet spot* (max2 4262 > max20 3704 < maxMAX 4055)
>
> **한계 — 한국·배달의민족 직접 통계 부재**:
> - 공개 자료 *없음* (내부 자료만 존재 추정).
> - 추후 *자체 측정 데이터*로 가정 갱신 필요 (Round 4·5에서 turn 분포 자체를 측정 변수로).
>
> **출처**:
> - [ebi.ai 챗봇 통계 2025](https://ebi.ai/blog/12-reliable-stats-on-chatbots-in-customer-service/)
> - [arXiv 2404.17025 — Conversation Length Satisfaction](https://arxiv.org/html/2404.17025v1)
> - [arXiv 1810.00278 — MultiWOZ](https://arxiv.org/pdf/1810.00278)
> - [Master of Code 챗봇 통계 2026](https://masterofcode.com/blog/chatbot-statistics)

---

## 2. `defaultValue = "default"` 폴백 정책의 위험 시나리오 2개+

`@RequestHeader(value = "X-Session-Id", defaultValue = "default")` — 헤더가 없을 때 모든 클라이언트가 같은 `"default"` 세션을 공유.

### 위험 시나리오 1 — 앱 업데이트 미이행 구버전 클라이언트

- 배달 앱 v1.0은 `X-Session-Id` 헤더를 보내지 않도록 출시
- v1.1부터 헤더 추가
- v1.0을 안 업데이트한 고객 N명이 동시에 챗봇 사용
- → N명의 대화가 모두 `"default"` 단일 세션 공유
- → **고객 A의 주문번호가 고객 B의 다음 발화에서 "그거"로 해석됨**
- 실제 사고: 1234 주문한 A → 1239 환불 받고 싶은 B → B의 "그거 취소해주세요" → A의 1234 취소 시도

**왜 위험한가**: Round 3 측정 발견 6 (응답-실재 분리)에서 본 *"LLM이 Memory만 보고 추론"* 패턴이 *완전히 다른 고객의 정보*로 일어남. **정보 무단 노출 + 잘못된 액션 동시 발생**.

### 위험 시나리오 2 — 어뷰저 의도적 헤더 누락 (IDOR-급)

- 악의 사용자 X가 `X-Session-Id` 헤더 *의도적으로 제거*
- 다른 고객들의 *방금 발화*가 `"default"` 세션에 누적됨
- X가 *"그 주문 어디쯤?"* 호출 → Memory에서 *다른 고객의 orderId* 추출 → Tool 호출 → **타인 주문 정보 조회 성공**
- 라이더 위치, 메뉴, 결제 금액 등 누출

**왜 위험한가**: 시나리오 4의 *세션 분리* 보호 기제가 *헤더 누락만으로 우회됨*. 인증·인가 없이 정보 접근 가능. IDOR(Insecure Direct Object Reference)와 동일 카테고리.

### 위험 시나리오 3 — 운영팀 자체 테스트 호출의 오염

- 운영팀이 `curl`로 직접 API 호출 (헤더 안 보냄)
- 운영 트래픽과 같은 `"default"` 세션에 운영팀 발화 누적
- 운영 고객이 *"그거"* 물으면 운영팀 발화 영향 받음
- 디버깅 vs 실서비스 데이터 경계 무너짐

### 프로덕션 대응
- `defaultValue = "default"` 제거 → 헤더 없으면 `400 Bad Request`
- 또는 *서버 발급 sessionId* (헤더 없으면 새 UUID 생성, Set-Cookie로 클라이언트 저장)
- 측정 결과로 본 보안 신호: Round 3 시나리오 4에서 세션 분리는 헤더가 *정확히 다른 값*일 때만 작동. *동일 값 공유* = 격리 무력화.

> **사용자 답** (리서치 + CVE 검증):
>
> **시나리오 #1·#2 둘 다 critical**. 시나리오 #3(운영팀 테스트)은 부수적.
>
> ### 시나리오 #1 — 앱 업데이트 미이행 구버전 클라이언트 (CRITICAL)
>
> | 항목 | 내용 |
> |---|---|
> | 트리거 | 앱 v1.0이 헤더 미발신 + v1.1 헤더 추가 → 일부 v1.0 사용자 잔존 |
> | 결과 | v1.0 사용자 N명 → 모두 `"default"` 단일 세션 공유 → 한 명 발화가 다른 사람 Memory에 누적 |
> | 공격 성격 | **부주의 사고** (악의 없음, 운영 환경 그 자체가 트리거) |
> | OWASP | **A01 Broken Access Control** + **A07 Auth Failures** |
> | CVE 매핑 | **CVE-2026-41712** (Spring AI 1.0.0–1.0.x `DEFAULT_CONVERSATION_ID`, **CVSS 7.5 High, Confidentiality:High**) — 본 프로젝트 *직접 영향권* |
> | 대표 사고 | ChatGPT 2023-03 redis-py 세션 분리 결함 → Plus 1.2% 영향, *대화 제목·결제정보 노출* (9시간) |
>
> ### 시나리오 #2 — 어뷰저 의도적 헤더 누락 (CRITICAL, IDOR-급)
>
> | 항목 | 내용 |
> |---|---|
> | 트리거 | 악의 사용자 X가 `X-Session-Id` 헤더 *의도적*으로 제거 후 호출 |
> | 결과 | 다른 고객 발화가 `"default"` 세션에 누적 → X가 *"그 주문 어디쯤?"* 호출 → Memory에서 *타인 orderId* 추출 → Tool 호출로 *타인 주문 정보 조회 성공* |
> | 공격 성격 | **적극적 공격** (의도적 행위, 자동화 가능, 탐지 어려움) |
> | OWASP | **API1:2023 BOLA (Broken Object-Level Authorization)** — API 공격의 ~40% 차지하는 #1 risk |
> | 추가 CVE | **CVE-2026-40966** (cross-tenant memory exfiltration via conversation ID manipulation, CVSS 5.9) — 추측·열거 공격이 *defaultValue fallback과 결합 시 추측 난이도 0* |
>
> ### #1 vs #2 차이
>
> - **#1**: 부주의 사고. *운영 환경의 자연 트리거* (앱 미업데이트). 대응 = UX·롤아웃 정책 + 코드 수정.
> - **#2**: 적극적 공격. *공격자가 명시적으로 트리거*. 대응 = 보안 가드 + 인증·인가 강화.
> - 둘 다 *동일한 fallback 패턴* 때문에 발생. 코드 수정(fail-fast)만 해도 **양쪽 모두 차단**.
>
> ### 즉시 적용할 대응 (외부 자료 기반 표준)
>
> 1. **`defaultValue` 제거 + `required=true`** — 누락 시 400 Bad Request (Spring AI 1.0.7/1.1.6이 채택한 fail-fast 모델)
> 2. **서버 발급 UUID** — `X-Session-Id` 누락 시 서버가 UUIDv4 발급 + 응답 헤더로 echo (OWASP Session Cheat Sheet)
> 3. **`(authenticatedUser, conversationId)` 매핑 검증** — BOLA mitigation (OWASP API1)
> 4. **Spring AI 1.0.7 또는 1.1.6 업그레이드** — `DEFAULT_CONVERSATION_ID` 상수 자체 제거됨, 컴파일·런타임에서 fail-fast 강제
>
> **결론**: 본 프로젝트의 `defaultValue = "default"` 패턴은 *가설이 아닌 CVE 번호 부여된 동일 결함*. 발제의 *"심각한 개인정보 사고"*가 **CVSS 7.5 (High)** 평가와 정확히 일치.
>
> **출처**:
> - [CVE-2026-41712 — Spring AI ChatMemory DEFAULT_CONVERSATION_ID cross-user data leakage](https://spring.io/security/cve-2026-41712/)
> - [HeroDevs — 5 Spring AI CVEs Disclosed 2026-04 (CVE-2026-40966 cross-tenant memory exfiltration 포함)](https://www.herodevs.com/blog-posts/5-spring-ai-cves-disclosed-april-27-2026-roundup-and-eol-risk)
> - [OWASP A01:2021 Broken Access Control](https://owasp.org/Top10/2021/A01_2021-Broken_Access_Control/)
> - [OWASP A07:2021 Identification & Auth Failures](https://owasp.org/Top10/2021/A07_2021-Identification_and_Authentication_Failures/)
> - [OWASP API1:2023 BOLA](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
> - [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
> - [Bitdefender — ChatGPT 2023-03 chat history leak](https://www.bitdefender.com/en-us/blog/hotforsecurity/chatgpt-bug-leaks-users-chat-histories)
> - [Spring AI 1.0.7/1.1.6 Release Notes](https://spring.io/blog/2026/05/08/spring-ai-1-0-7-1-1-6-2-0-0-M6-available-now/)

---

## 3. 세션 식별 4 방식 비교

발제 3.2 표 기반 + 배달 상담 도메인 적용 시 장단점.

| 전략 | 장점 | 단점 | 배달 상담 적합성 |
|---|---|---|---|
| **HTTP 헤더 (`X-Session-Id`)** | • 프레임워크 독립<br>• 모든 클라이언트(앱/웹/API) 지원<br>• `@RequestHeader`로 간단 | • 클라이언트가 sessionId 생성·관리 → **위조 가능**<br>• 헤더 누락 시 fallback 정책 필요 (위험 #2) | ⭐⭐⭐ 범용 (Round 3 채택). 단 위조 방어책 필요 |
| **쿠키 / HTTP Session** | • 브라우저 자동 처리<br>• 서버 발급 → 위조 어려움<br>• 만료·갱신 표준 메커니즘 | • Sticky Session 필요 (멀티 인스턴스 부담)<br>• 모바일 앱·서버 간 호출엔 어색<br>• CORS 설정 복잡 | ⭐ 웹 챗봇 단독이면 OK. 앱·API 혼재면 부적합 |
| **JWT 클레임 (`sub` 또는 `sessionId` claim)** | • 인증과 통합<br>• 서명 검증으로 위조 방어<br>• 멀티 인스턴스에 무관 | • JWT 발급·검증 인프라 필요<br>• 무인증 챗봇엔 과잉<br>• 토큰 만료·갱신 흐름 추가 | ⭐⭐⭐⭐ **이미 JWT 인증 있는 서비스 최적**. 배달 앱이 로그인 기반이면 권장 |
| **URL 경로 (`/session/{id}/chat`)** | • 명시적<br>• 로그·디버깅 추적 쉬움 | • URL 길어짐<br>• REST 의미 깨짐 (세션은 리소스가 아님)<br>• sessionId가 URL에 노출되어 로그·캐시·Referer에 누출 | ⭐ 비추천 — 운영 API로 부적합 |

**배달 상담 도메인 종합 추천**:
1. *인증 기반 배달 앱* → **JWT 클레임** (sub로 고객 ID, sessionId는 별도 클레임)
2. *무인증 챗봇 위젯 (웹)* → **쿠키** + HttpOnly + Secure
3. *외부 파트너 API* → **HTTP 헤더** + API Key + 위조 방어 (서명 또는 server-issued sessionId)

> **사용자 답** (리서치 + 실서비스 사례 기반):
>
> **선택: A — HTTP 헤더 `X-Conversation-Id` (UUID v4) 유지 + 결함만 수정**.
>
> ### 핵심 분리 원칙 (메이저 챗봇·배달 서비스 합의)
>
> *"인증 토큰(누가)" ≠ "대화 ID(무엇을)"* — 모두 **두 레이어 분리** 사용:
>
> | 서비스 | 인증 (누가) | 대화 ID (무엇을) |
> |---|---|---|
> | ChatGPT | 쿠키 `__Secure-next-auth.session-token` | 별도 conversation |
> | Claude API | (Anthropic key) | `conversation_uuid` 헤더 |
> | **배민** (우아콘 2024) | 게이트웨이 JWT | 별도 ID |
> | DoorDash | JWT | 별도 conversationId |
> | UberEats | JWT (OAuth2) | 별도 conversationId |
>
> ### 4 방식 비교
>
> | 방식 | Spring AI 1.0 정합 | 보안 | 멀티 인스턴스 | 채택 사례 |
> |---|---|---|---|---|
> | **HTTP 헤더** | 상 (`ChatMemory.CONVERSATION_ID` 직결) | 중 (UUID 검증 필요) | stateless ✓ | Claude API, Botpress |
> | 쿠키 | 중 (필터 추출 필요) | 상 (HttpOnly+Secure) | sticky 필요 | ChatGPT |
> | JWT 클레임 | 중 (claim 별도 추출) | 중하 (즉시 무효화↓) | 완전 stateless | 배민 게이트웨이 |
> | URL 경로 | 하 | **하 — OWASP 금지** (Referer·로그·히스토리 누출) | 가능 | (anti-pattern) |
>
> ### Round 3 (무인증 챗봇) 선택 — A의 구체 변경
>
> 1. `X-Session-Id` → **`X-Conversation-Id`** 리네이밍 (의미 명확화: 인증 ≠ 대화 ID)
> 2. **`defaultValue="default"` 제거** + `required=true` (CVE-2026-41712 즉시 차단)
> 3. 빈 헤더 시 *서버가 UUIDv4 발급* → 응답 헤더로 echo (클라이언트가 다음 호출부터 보존)
> 4. **UUID 정규식 검증** 추가 — `^[0-9a-fA-F]{8}-...$` 형식 강제 (임의 문자열 거부)
> 5. Spring AI `ChatMemory.CONVERSATION_ID` 파라미터에 그대로 매핑 (코드 최소 변경)
>
> ### 미래 확장 — 로그인 도입 시 자연 진화
>
> 무인증 위젯 → 인증 배달 앱으로 진화 시:
> - **Authorization: Bearer JWT** (`sub` = userId) 추가
> - **X-Conversation-Id** 헤더 그대로 유지
> - ChatMemory 키 = `user:{userId}:{conversationId}` 형태로 영속화
> - 즉 *지금 A 선택이 추후 두 레이어 패턴으로 *자연 확장* 가능*
>
> ### 왜 B·C는 *지금* 아닌가
>
> - **B** (SessionResolver 추상화): 학습 폭 ↑이지만 *Round 3 발제 의도*는 `defaultValue` 결함 시뮬레이션. A로 충분.
> - **C** (쿠키 + JWT 하이브리드): 보안 최강이지만 *Round 3 범위 초과* (CORS·CSRF·credentials 설정 추가 부담).
>
> ### Round 3 측정 데이터와 정합
>
> - 시나리오 4 (다른 sessionId 격리 100%) → A의 *서버 발급 UUID*가 *우연한 동일 값* 사고 차단
> - DESIGN_DECISIONS Q2의 CVE-2026-41712 즉시 대응
>
> **출처**:
> - [Spring AI ChatMemory docs](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
> - [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
> - [Claude API conversation_uuid](https://platform.claude.com/docs/en/agent-sdk/sessions)
> - [배민 우아콘 2024 API Gateway](https://youngwon.io/woowahan-api-gateway/)
> - [DoorDash JWT 인증](https://developer.doordash.com/en-US/docs/drive/reference/JWTs/)
> - [UberEats OAuth2 인증](https://developer.uber.com/docs/eats/guides/authentication)
> - [Session ID Leakage via Referer (URL 경로 위험)](https://beaglesecurity.com/blog/vulnerability/session-id-leakage-via-referer-header.html)

---

## 4. 클라이언트가 sessionId를 정하는 방식의 보안 리스크

Round 3 채택한 방식: 클라이언트가 임의로 `X-Session-Id` 값 정함. 발제 3.4 데모처럼 `customer-A`, `customer-B` 같은 임의 문자열.

### 리스크 1 — 세션 ID 위조·열거 (Session Hijacking)

악의 사용자가 *다른 고객의 sessionId 추측·열거*:
- 시퀀셜 sessionId (예: `user-1234`, `user-1235`): 직관적으로 추측 가능
- 짧은 UUID 일부분: brute-force 가능
- 추측 성공 → 그 세션의 Memory 접근 → **타인 대화 이력 조회 + 그 맥락으로 Tool 호출**

### 리스크 2 — sessionId 자체가 PII 누출 경로

클라이언트가 sessionId를 *전화번호·이메일·고객 ID*로 정하면:
- 서버 로그에 sessionId 노출 → 평문 PII 저장
- 디버깅용 SessionController.ids API(`GET /api/v1/session/ids`)가 *모든 활성 sessionId 노출* → PII 일괄 노출

### 리스크 3 — sessionId 공유로 인한 다중 디바이스 동시 사용

같은 sessionId를 여러 디바이스에서 동시 사용:
- 동시 호출이 같은 Memory에 누적 → race condition (Memory 일관성 깨짐)
- 적절한 격리 없으면 의도치 않은 대화 혼선

### 방어책 (수업 내용 + 발제 힌트)

| 방어책 | 효과 |
|---|---|
| **서버 발급 UUID** | 클라이언트가 정한 sessionId 거부, 첫 요청 시 서버가 UUIDv4 발급 → Set-Cookie 또는 응답 헤더 |
| **JWT 서명 검증** | 클라이언트가 보낸 sessionId가 *서버 서명 토큰* 안에 있어야만 인정. 위조 불가 |
| **sessionId 형식 검증** | UUID 정규식 강제. 임의 문자열(`customer-A`) 거부 |
| **세션-사용자 바인딩** | 인증된 사용자 ID와 sessionId 매핑. 다른 사용자가 같은 sessionId 호출 시 거부 |
| **TTL + 자동 만료** | 일정 시간(예: 30분) 미사용 시 Memory clear |
| **세션 ID 로깅 마스킹** | log.info에서 sessionId 일부만 출력 (예: `user-12**`) |
| **Admin API 인증·인가** | `GET /api/v1/session/ids` 같은 디버깅 API에 운영자 권한 검사 |

### Round 3 측정 결과로 본 검증

- 시나리오 4: `customer-A`, `customer-B` 같은 *클라이언트 정의* sessionId가 *우연히 다른 값*이면 격리는 작동 (B에 1234 노출 0/20)
- 하지만 *우연히 같은 값* (또는 위조)이면 격리 무력화 — 측정으로는 *부정 사례* 없음 (애초에 같은 값 안 줬으니), 위험은 *이론*

> **사용자 답** (산업 표준 리서치 + CVE 검증 기반):
>
> **클라이언트 정의 sessionId 리스크 = 4중**: session fixation + IDOR + enumeration + cross-session leak.
> 산업이 막는 방식 = *5단계 방어 패턴*. **Round 3 즉시 Top 3 + 프로덕션 Top 3** 둘 다 명시.
>
> ### 산업 합의 — 5단계 방어 패턴
>
> | Layer | 목적 | 핵심 기법 | 근거 |
> |---|---|---|---|
> | **1. 발급** | 클라이언트 신뢰 금지 | 서버 CSPRNG **UUID v4** 발급, 응답 헤더 echo, *클라 입력 ID 무시* | OWASP Session Mgmt, NIST SP 800-63B, RFC 6265 |
> | **2. 형식** | 엔트로피·예측 차단 | 128-bit 난수, 정규식 화이트리스트, *sequential·timestamp·user-derived 금지* | OWASP Insufficient Session-ID Length, CWE-384 |
> | **3. 검증** | fixation·IDOR 차단 | 서버 store 매핑 확인, `(userId ↔ conversationId)` 바인딩, *부재/위조 시 동일 응답* | CVE-2026-41712, OWASP API #1 BOLA, LangGraph `@auth.on` |
> | **4. 운영** | leak·재사용 차단 | 로그·트레이스 **마스킹**, idle/absolute **TTL**, 권한 변경 시 **회전**, 로그아웃 시 server-side 무효화 | ChatGPT 2023 사고, LinkedIn 3개월 쿠키 사고 |
> | **5. 접근 제어** | 관리 API 보호 | Admin endpoint 인증·**rate-limit**, enumeration 방어, *존재/비존재 응답 동일화* | Burp Sequencer/ZAP 분석, CVE-2025-32975 |
>
> ### ⛔ 반드시 막아야 할 3 패턴 (CVE·사고로 검증)
>
> 1. **`DEFAULT_CONVERSATION_ID` 류 전역 암묵 기본값** — Spring AI CVE-2026-41712, OpenAI ChatGPT 2023 redis-py 사고 동일 원인
> 2. **owner 검증 없이 클라이언트 ID 신뢰** — IDOR/BOLA (Grafana CVE-2024-1313), session fixation (OWASP)
> 3. **예측 가능·저엔트로피 ID + 평문 로그** — Burp Sequencer·OWASP ZAP로 enumeration 자동화, CitrixBleed 류 hijack
>
> ### 🎯 Round 3 즉시 적용 Top 3 (학습 범위)
>
> | # | 방어책 | 이유 |
> |---|---|---|
> | **1** | **서버 UUID v4 발급 강제** (Layer 1) | Q3 결정과 정렬. 클라 입력 ID 신뢰 차단 → **CVE-2026-41712 제거**. 코드 1곳, 비용 0. |
> | **2** | **정규식 화이트리스트 검증** (Layer 2) | `^[0-9a-f-]{36}$` UUID 패턴 강제. 위조·enumeration 페이로드 거부. 시나리오 4 *부정 사례 측정 공백*을 코드로 메움. |
> | **3** | **로그·트레이스 마스킹** (Layer 4 일부) | `PerformanceLoggingAdvisor`·디버그 로그의 conversationId 평문 노출 시 cross-session leak. 앞 6자만 (`abc123**`). 측정 데이터 공개 시에도 안전. |
>
> ### 🏭 프로덕션 Top 3 (Round 4·5 이월)
>
> | # | 방어책 | 이유 |
> |---|---|---|
> | 1 | **owner 바인딩 + 서버 store 검증** (Layer 3) | `userId↔conversationId` 매핑 → IDOR/BOLA 차단. **CVE-2026-40966** (cross-tenant memory exfiltration) 근본 원인. 멀티유저 전환 시 필수. |
> | 2 | **TTL + 권한 변경 시 회전** (Layer 4) | NIST SP 800-63B 표준 (idle/absolute timeout, regenerate on privilege change). LinkedIn 3개월 쿠키·Firesheep 사례가 부재 시 피해 규모를 보여줌. |
> | 3 | **Admin API 인증 + rate-limit** (Layer 5) | `SessionController` (`/sessions`, reset 등) enumeration·대량 삭제 차단. Quest KACE CVE-2025-32975 류 관리자 우회 + *응답 동일화로 존재 추측 봉쇄*. |
>
> ### Round 3 측정 데이터와 정합
>
> - 시나리오 4 (격리 100%, 다른 sid 가정) → Top 3의 *서버 UUID + 정규식*이 *부정 사례 측정 공백*을 코드 보장으로 메움
> - DESIGN_DECISIONS Q2의 CVE-2026-41712 즉시 대응 = Top 3 #1과 일치
> - Q3 결정 (`X-Conversation-Id` UUID v4 헤더) = Top 3 #1·#2 자연 적용
>
> ### 사용자 운영 통찰 (이 답의 출발점)
>
> *"클라이언트가 직접 정하게 되면 막 보내서 다 뚫린다"* — 정확. 산업 합의는 **서버 측 데이터 이관 (서버 발급 + 서버 store + 서버 검증)**. 그 다음 *형식·운영·접근제어* 4개 layer.
>
> **출처**:
> - [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
> - [NIST SP 800-63B Session Management](https://pages.nist.gov/800-63-4/sp800-63b/session/)
> - [OWASP Insufficient Session-ID Length](https://owasp.org/www-community/vulnerabilities/Insufficient_Session-ID_Length)
> - [OWASP API #1 BOLA](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
> - [RFC 6265 — HTTP State Management](https://www.rfc-editor.org/rfc/rfc6265.html)
> - [CVE-2026-41712 (Spring AI ChatMemory DEFAULT_CONVERSATION_ID)](https://spring.io/security/cve-2026-41712/)
> - [CVE-2026-40966 (Spring AI cross-tenant memory exfiltration)](https://spring.io/security/cve-2026-40966/)
> - [LangGraph 인증·자원 권한 가드](https://docs.langchain.com/langgraph-platform/auth)
> - [Cross-Session Leak in AI Assistants (Giskard)](https://www.giskard.ai/knowledge/cross-session-leak-when-your-ai-assistant-becomes-a-data-breach)
> - [Cobalt — LLM Chatbot Security Risks](https://www.cobalt.io/blog/security-risks-of-llm-powered-chatbots)
> - [ChatGPT 2023-03 redis-py session leak](https://openai.com/index/march-20-chatgpt-outage/)
> - [CWE-384 Session Fixation](https://cwe.mitre.org/data/definitions/384.html)

---

## 부록 — 측정 데이터 요약 (참고용)

| 항목 | 값 | 출처 |
|---|---|---|
| 평균 응답 시간 | T1 ~8s, T2 ~9s | scn1~5 평균 |
| Memory 메시지 수 | 4 msgs / 2턴 시나리오 | 모든 trial |
| 세션 격리 성공률 | 20/20 (100%) | scn4 |
| DELETE 작동률 | 20/20 (100%) | scn5 |
| Tool 호출 총량 | 52 / 100 trials | 모든 시나리오 |

자세한 raw + 발견: `EXPERIMENT_LOG_QUEST1_DRAFT.md`

---


> QUEST 2단계 "설계 결정 문서" 4 질문에 대한 draft 답.
> AI가 draft 작성 → 사용자가 자기 답으로 수정·승인.

---

## 1. 슬라이딩 윈도우 vs 대화 요약(summarization) 전략 — 장단점 표

| 차원 | `MessageWindowChatMemory` (슬라이딩) | 요약 (Summarization) |
|---|---|---|
| **구현 복잡도** | Spring AI 기본 제공 (1 줄) | Spring AI 1.0 미제공, 직접 `ChatMemory` 구현 필요 |
| **정보 손실** | *오래된 메시지 완전 삭제* (binary) | *압축* (정보 일부 보존, 정확도 일부 손실) |
| **입력 토큰** | 최근 N개 × ~50토큰 = O(N) 일정 | 요약 길이 + 최근 K개 = O(K) 일정 (예측 가능) |
| **시간 의존 정보** | 옛 정보 회수 *불가* (윈도우 밖) | 요약에 *시간 핵심 정보* 포함 가능 |
| **요약 LLM 호출 비용** | 0 | *매 N턴마다 추가 LLM 호출* (요약 생성 비용) |
| **결정성** | *fully deterministic* (N개 자르기) | LLM 요약 품질에 의존 — 비결정성 |
| **toolMessage 처리** | Memory 외부 (자르기 대상 X) | 요약 시점에 *Tool 결과도 포함 가능* (장점) |
| **배달 상담 적합 영역** | 짧은 단발 상담 (5~10턴) | *길고 복잡한 환불 협의* (50턴+) |

**요약이 더 적합한 시나리오**:
- 30턴+ 장기 상담 (배달 사고·환불 협의)
- *오래된 정보가 *주기적*으로* 회수되는 패턴 (예: 첫 발화의 주문번호를 25턴 후 다시 참조)
- 비용 제약 (입력 토큰 *상한*이 명확해야 함)

**슬라이딩이 더 적합한 시나리오**:
- *짧은 세션* (Round 3 측정의 1·2단계처럼 5~10턴)
- *비결정성을 못 받아들이는 운영* (디버깅 reproducibility 중요)
- *Spring AI 1.0 기본 제공*이라 *추가 구현 비용 0*

**Round 3 측정으로 본 *추가 발견***:
- 2단계 b 30턴에서 `MAX_MESSAGES = 20` 가 *토큰 V자 최저점* (max2 4262 > max20 3704 < maxMAX 4055).
- → 슬라이딩이라도 *적절 N 선택*하면 30턴까지 충분. 50턴+에서 요약 필요.

> **사용자 답** (학술 정합 + 단계적 검증, A + C 혼합):
>
> 사용자 가설 *"슬라이딩 = 정확성 ↑, 단 환각 가능성 ↑ ; 요약 = 50% 정도면 충분"*은 **학술 결과로 정교화 + 단계적 검증 계획**으로 정리.
>
> ### 가설 검증 표 (Workflow 4-agent 리서치 결과)
>
> | 사용자 가설 | 결과 | 근거 |
> |---|---|---|
> | **#1 슬라이딩 = 정확성 ↑** | **조건부 참** — 윈도우 안 정보만 | [Lost in the Middle (arxiv 2307.03172)](https://arxiv.org/abs/2307.03172) U-shape — 중간 위치 정보는 long-context 모델도 급락. Round 3 30턴 max20 회수 10/10 정합 |
> | **#2 환각 가능성 ↑** | **참, 단 원인 정교화** | [LongHalQA (2410.09962)](https://arxiv.org/abs/2410.09962) long-form 78.8%·멀티턴 82.4% 환각. [인과 분석 (2510.20229)](https://arxiv.org/abs/2510.20229): 길이 자체가 아닌 **컨텍스트 의존 누적 + 응답 길이**가 원인 |
> | **#3 요약 50% 충분** | **지표 의존 — 부분 기각** | [CogCanvas (2601.00821)](https://arxiv.org/pdf/2601.00821): ROUGE-L 0.5·BERTScore 0.85+은 의미 보존 OK, 단 **entity exact-match 19% vs verbatim 93%**. 배달 주문번호·금액·알러지엔 부족 |
>
> ### 길이별 전략 (단계적 도입)
>
> | 대화 길이 | 전략 | Round 매핑 |
> |---|---|---|
> | **단기 (5~10턴)** | 순수 슬라이딩 `MessageWindowChatMemory(20)` | **Round 3 (현재) — 유지** |
> | **중기 (10~30턴)** | `ConversationSummaryBufferMemory` 하이브리드 — 최근 K턴 verbatim + 오래된 턴 누적 요약 | Round 3 후반 ~ Round 4 초 (계획) |
> | **장기 (50턴+)** | Summary + **RAG (PgVector)** + structured slot 분리 (주소·메뉴·알러지) | Round 4 ~ Round 5 (계획) |
>
> ### Round 3 결정 — 슬라이딩 유지 + 가설 정량 측정 보고
>
> 1. 현 코드: `MessageWindowChatMemory(20)` 유지 (V자 sweet spot 측정 정합)
> 2. **사용자 가설 정교화** 결과를 EXPERIMENT_LOG_QUEST2 + 본 문서에 학술 근거와 함께 기록
> 3. Round 3 후속 실험 제안 — *needle 위치(앞/중/끝) × 응답 길이* 매트릭스 (Lost in the Middle 검증 응용)
> 4. **"50% 충분" 기준 재정의** — ROUGE-L 0.5보다 **entity exact-match 50%** (CogCanvas 권장)
>
> ### Round 4 PgVector — *3-way 비교 실험* 설계
>
> - **A. 슬라이딩 only** (현 Round 3)
> - **B. SummaryBuffer 하이브리드** (LangChain 표준 패턴)
> - **C. Summary + RAG** (Vector 회수 + slot 추출)
> - 측정 지표: 입력 토큰 / 응답 시간 / **entity exact-match** / 환각률 (Tool 응답 vs LLM 응답 불일치)
> - 시나리오: Round 3 30턴 시퀀스 + 환불 협의 (24h 후 재방문) 시뮬레이션
>
> ### Round 3 측정 데이터와 정합
>
> - 30턴 max20 V자 토큰 sweet spot — *슬라이딩 한계 도달 시점 정량* (50턴+에서 폭증 예상)
> - max2 turn 30 요약 84자(1라운드만) vs max20 148자 — *요약 메모리 도입 시 *기대 효과* 정량적 baseline*
> - max2 옛 정보 회수 7/10 — *시퀀스 정보 분포 의존*. Lost in the Middle U-shape와 같은 패턴
>
> ### 핵심 논문 5개
>
> - [Lost in the Middle (2307.03172)](https://arxiv.org/abs/2307.03172) — U-shape, 가설 #1 정교화
> - [Why LVLMs Hallucinate More in Longer Responses (2510.20229)](https://arxiv.org/abs/2510.20229) — 가설 #2 정교화 (길이 ≠ 원인)
> - [Long Context vs RAG (2501.01880)](https://arxiv.org/abs/2501.01880) — LC vs RAG 적용 영역
> - [CogCanvas Verbatim vs Summary (2601.00821)](https://arxiv.org/pdf/2601.00821) — 가설 #3 정량화
> - [Mem0 State of AI Agent Memory 2026](https://mem0.ai/blog/state-of-ai-agent-memory-2026) — 산업 하이브리드 표준
>
> **결론**: *Round 3 = 슬라이딩 유지 + 가설 정교화 기록. Round 4·5 = 하이브리드·RAG 도입 + 3-way 비교 실험.*

---

## 2. "지시 대명사 해결 성공률" 프로덕션 금지 기준

**기준 (제안)**: **10턴 중 7회 미만 = 프로덕션 금지**.

**근거** (Round 3 측정):

| MAX | T8 "아까 1234는" 정확도 | 평가 |
|---|---:|---|
| 2 | 4/10 (40%) | ❌ 절반 이상 실패 = 사용자 *반복 발화* 강제 = UX 파괴 |
| 2 (30턴) | 7/10 (70%) | △ 시퀀스 정보 분포에 의존 |
| 20 | 10/10 (100%) | ✓ |
| 20 (30턴) | 10/10 (100%) | ✓ |

**70% 기준 정당화**:
- 100% 보장은 LLM 특성상 불가능 (qwen2.5 stochastic + 한국어 noise)
- 70%면 *3/10 실패* — 고객이 *반복 발화*해야 풀림. 짜증나지만 *서비스 작동*은 함.
- 60% 이하 = *반복해도 풀리는지 불확실* → 신뢰성 단절.

**보완 가드**:
- LLM이 *"어떤 주문 말씀이신가요?"* 응답 시 *fallback flow*로 안내 (성공률 일부 손실 보정)
- *동일 대화에서 같은 지시 대명사 2번 실패*면 자동으로 상담원 연결

**한 단계 더 — 도메인 차등**:
- *주문 조회* (read-only): 70% 허용
- *주문 취소·환불* (write, 비가역): **95% 미만 금지** — *잘못된 orderId로 취소*는 *돌이킬 수 없는 사고*

> **사용자 답** (Round 3 측정 1차 근거 + 산업·학술 리서치 참고):
>
> **선택: A — read ≥75% / write ≥90% + HITL (Human-In-The-Loop) confirm 게이트**.
>
> ### 1차 근거 — Round 3 측정 데이터
>
> | 발화 형태 | Round 3 측정 | 판정 |
> |---|---:|---|
> | "그거" (scn1 T2, 가장 모호) | 9/20 (**45%**) | **read 75% 미달** → 인계 흐름 필수 |
> | "아까 물어본 그 주문" (scn3 T2) | 14/20 (**70%**) | 경계선 — read 75% 임계 *약간 미달* |
> | "그거 말고 1235" (scn2 T2, 새 orderId 명시) | 19/20 (**95%**) | write 90% 도달, *단 HITL 추가 권장* |
> | T8 "아까 1234는" max=20 (10턴) | 10/10 (**100%**) | 윈도우 충분 시 옛 정보 회수 완벽 |
> | T8 max=2 (10턴) | 4/10 (**40%**) | 윈도우 부족 시 *프로덕션 금지* |
>
> → **측정 데이터가 임계 결정에 1차 근거**. *"그거 45%"는 production-ready 출발선 미달*이라는 직접 진단.
>
> ### 보조 근거 — 산업·학술·도메인 리서치 (참고)
>
> | 축 | 산업 SLA | 학술 컨센서스 | 배달/CS 도메인 |
> |---|---|---|---|
> | Read (조회) | confidence 60-79% verification, ≥80% 자동 | DST SOTA ~76%, F1 ≥70% "양호" | FCR 평균 70%, 챗봇 단독 65-73% |
> | Write (write, 비가역) | critical 98%+, transactional은 HITL | 단일 임계 부재, expert-in-loop 필수 | 신뢰도 ≥70% + 금액 컷오프 + 2-3회 실패 escalation |
>
> → Round 3 임계 (75/90)가 *산업 하한*과 일치, *critical 98%* 보다는 *학습 프로젝트* 영역에서 합리적.
>
> ### Read vs Write 차등 정당화
>
> | 항목 | Read | Write |
> |---|---|---|
> | 비가역성 | 회복 가능 (재질문) | 비가역 (결제·삭제) |
> | 잘못된 응답 비용 | 짜증 + UX 손실 | **데이터 손실 + 환불·법적 분쟁** |
> | Round 3 임계 | **≥75%** | **≥90% + HITL** |
>
> ### Escalation 3축 게이트 (배달 도메인 표준)
>
> 1. **confidence < 70%** → 즉시 상담원 인계
> 2. **2-3회 실패 응답** → 강제 escalation
> 3. **금액·정책 임계 초과** (예: 환불 5만원+) → 인간 승인
>
> ### Round 3 적용 액션 (구체)
>
> 1. *"그거" 같은 모호 지시 대명사*에 대한 LLM 신뢰도 측정 → **<75%면 *"어떤 주문 말씀이신가요?"* 되묻기 fallback flow** (현 Round 3는 이미 일부 trial에서 자연 발생)
> 2. `cancelOrder` Tool 호출 전 **사용자 confirm 1단계** 추가 — *"2024-1235 주문을 취소합니다. 진행할까요? (yes/no)"* (HITL 게이트)
> 3. 동일 발화 *반복 시 자동 상담원 인계* — 사용자가 같은 의도를 2-3회 반복하면 LLM 신뢰도 낮음
> 4. Round 4 PgVector에서 *retrieval confidence*로 임계 측정 가능 → escalation 자동화 정교화
>
> ### 핵심 시사점
>
> - 차등 방향성 (read·write) = 산업 컨센서스
> - HITL 게이트 = critical 비가역 액션의 *표준 안전망*
> - Round 3 측정 "그거 45%" = production-ready 미달 → 인계 흐름 설계 우선순위
>
> **출처** (보조 자료):
> - [Intercom Chatbot CSAT KPI](https://www.intercom.com/help/en/articles/9439256-chatbot-csat)
> - [Botpress Containment Rate Benchmark](https://botpress.com/blog/containment-rate)
> - [Zendesk AI Agent Confidence Threshold](https://www.eesel.ai/blog/zendesk-ai-agent-intent-confidence-threshold)
> - [Sobot FCR Metrics 2025](https://www.sobot.io/article/first-contact-resolution-metrics-2025/)
> - [LLM Hallucination Statistics 2025](https://sqmagazine.co.uk/llm-hallucination-statistics/)
> - [AI Refunds Returns Disputes Automation (Fini)](https://www.usefini.com/guides/ai-refunds-returns-disputes-automation)
> - [Chatbot Human Handoff Guide](https://www.conferbot.com/blog/chatbot-human-handoff-guide)

---

## 3. 배달 상담에서 "오래된 대화"가 의미 있는 케이스

배달 상담 평균 5~10턴이지만 *오래된 대화 회수*가 의미 있는 시나리오:

### 케이스 1 — 환불 협의 (시간 흐름 큰 경우)

- "어제 주문한 1234 환불 어떻게 진행됐어요?"
- *24시간 전 대화*에서 *환불 신청 사유·고객 답변·약속 일정*을 회수해야 *일관된 안내*
- 슬라이딩 윈도우만으론 *반드시 손실*. 영속 저장 + 요약 전략 필수.

### 케이스 2 — 동일 고객 재주문 패턴

- 며칠 전 *"매운맛 빼주세요"* 같은 *반복 선호*를 회수해 *제안*
- *고객 단위* (Q4 참조) 영속화가 핵심.

### 케이스 3 — 상담원 인계 후 재방문

- 상담원 연결 → 30분 후 *같은 세션 재진입*
- *그 30분 사이 LLM에게 한 발화*를 회수해 *상담원 컨텍스트 보존*
- 메시지 큐 또는 redis pub-sub로 상담원 측에도 동기화

### 케이스 4 — 사고·민원 추적

- *3개월 전 사고 건* 재문의 — *법적 분쟁 자료*로 활용
- 영속 저장 + 감사 로그 (Round 3 3단계 발견)
- 5주차 Guardrail의 *PII 마스킹*과 결합 필수

> **사용자 답** (Round 3 본질 집중 + 리서치 첨언, A):
>
> ### Round 3 (in-memory + conversationId) 본격 케이스 3개
>
> | # | 케이스 | Round 3 처리 |
> |---|---|---|
> | **1** | **환불 협의 (24h 흐름)** | 단일 세션 내 환불 신청 사유·고객 답변·약속 일정 추적 |
> | **2** | **상담원 인계 컨텍스트** | 인계 *그 순간* 세션 메모리를 요약해 상담원 측에 전달 |
> | **3** | **🚨 토큰 폭증 대응 (요약 압축)** | 30턴+ 누적 시 `ConversationSummaryBufferMemory` 도입 (Round 3 후반) |
>
> ### Round 4·5 이월 케이스
>
> | 케이스 | 이월 사유 | Round |
> |---|---|---|
> | 동일 고객 재주문 패턴 (선호 보존) | *세션 내* 한정으로는 본질 가치 없음. *고객 단위 영속*이 본격 | Round 5 |
> | 사고·민원 추적 (3개월+) | in-memory 자동 휘발과 충돌. 법적 보존·추적 필수 | Round 4-5 (PgVector·고객 메모리) |
>
> ### 1차 근거 — Round 3 측정·발제 본질
>
> - 발제 시나리오 (2단계 b 30턴 누적): 환불 협의 24h 시나리오가 *직접 매핑*
> - 30턴 max20 토큰 3704·max2 4262 — *50턴+ 폭증 예상* → 요약 압축 필수
> - 발제 4.5 (Memory + Tool Calling 상호작용) — 인계 시 USER/ASSISTANT만 보존 → *Tool 결과는 자연어 응답에 녹임 필요*
>
> ### 첨언 — 리서치로 발견된 *AI draft에 없던* 신규 케이스 5개 (참고용)
>
> 4-agent Workflow 리서치가 발견한 *AI draft에 없던* 케이스:
>
> 1. **🚨 토큰 폭증 대응 (rolling summary)** — ChatGPT·Claude 표준. Round 3 본격에 채택 (위 #3)
> 2. **개인화 추천 (UberEats Rufus 형태)** — McKinsey 매출 10-15%↑. *재주문보다 한 단계 구체적* (메뉴·식이·결제수단 구조화 선호) → **Round 5 이월**
> 3. **반복 환불 어뷰즈 탐지** — Craver refund abuse 산업 이슈. *환불 협의의 그림자 측면* → P3 우선순위
> 4. **GDPR/개인정보 삭제권** — Round 5 영구 메모리의 정면 충돌. Round 3 in-memory 자동 휘발로 자연 회피
> 5. **상담원 인계 시 컨텍스트 자동 전달** — *82% 고객이 인계 시 재입력 강요* (BlueTweak) — *재방문 이전* 인계 *그 순간*이 핵심 KPI → Round 3 본격 (위 #2)
>
> ### 빈도·임팩트 첨언 (배달 도메인 통계)
>
> | 통계 | 출처 |
> |---|---|
> | **DoorDash 재주문 매출 65%**, 밀레니얼 27%가 24h 내 3회+ 주문 | DoorDash 운영 데이터 |
> | **상담원 인계 시 82% 재입력 강요** — UX·FCR·운영 비용 직격 | BlueTweak |
> | AI 챗봇 환불 분쟁이 CNBC 보도될 만큼 표준 페인포인트 | CNBC 2026-04 |
> | 한국 식품 위해 신고 48h 의무, 분쟁조정 장기 | 한국소비자원 |
>
> ### 출처 (첨언 자료)
>
> - [DoorDash Online Ordering Habits](https://merchants.doordash.com/en-us/blog/online-ordering-habits)
> - [BlueTweak — AI to Human Handoff 82% Re-entry](https://bluetweak.com/blog/ai-to-human-handoff/)
> - [Decagon — Escalation Rate](https://decagon.ai/glossary/what-is-escalation-rate)
> - [HK 소비자위원회 배달 플랫폼 민원](https://www.consumer.org.hk/en/press-release/p-584-food-delivery-platform-complaints)
> - [Craver — Refund Abuse 산업 이슈](https://www.getcraver.com/blog/delivery-app-refund-abuse/)
> - [CNBC 2026-04 AI 챗봇 환불 분쟁 보도](https://www.cnbc.com/2026/04/01/ai-chatbot-customer-service-complaints-refunds.html)
> - [Amazon Rufus 개인화 쇼핑](https://www.aboutamazon.com/news/retail/amazon-rufus-ai-assistant-personalized-shopping-features)
> - [McKinsey — Personalization Value](https://www.mckinsey.com/capabilities/growth-marketing-and-sales/our-insights/the-value-of-getting-personalization-right-or-wrong-is-multiplying)
> - [GDPR Chatbot Compliance](https://gdprlocal.com/chatbot-gdpr-compliance/)
> - [ChatGPT Memory Feature](https://openai.com/index/memory-and-new-controls-for-chatgpt/)
> - [Claude Memory (Simon Willison 2025-09)](https://simonwillison.net/2025/Sep/12/claude-memory/)

---

## 4. Memory를 *세션*이 아닌 *고객 단위*로 영속 유지하면?

### 새로 가능한 기능 (장점)

1. **개인화된 상담** — *전 주문 이력 + 선호 + 알레르기* 같은 정보 회수
2. **반복 패턴 학습** — *매주 같은 시간 주문* 같은 패턴 인지
3. **상담원 인계 시 풍부한 컨텍스트** — *지난 6개월 상담 이력*까지 전달 가능
4. **선제적 안내** — *지난 주문에서 사고 있었음 → 이번 주문 조심 안내*
5. **NPS·만족도 추적** — *불만 패턴* 자동 감지

### 새로 발생하는 리스크

1. **🚨 PII 영구 누적** — 전화·주소·결제 정보가 *영구 저장* → GDPR/개인정보보호법 *삭제 요청 권리* 보장 어려움. 5주차 Guardrail + *자동 삭제 배치* 필수.
2. **🚨 컨텍스트 폭발** — *6개월 이력*이 *매 호출 프롬프트*에 끼면 입력 토큰 *수만 토큰* → 비용 폭증 + LLM 컨텍스트 윈도우 초과
3. **🚨 stale 정보 사고** — *주소 바뀌었는데 옛 주소 회수* → *틀린 배달지 안내*. *시간 가중치* 적용 필요.
4. **인증·소유권** — *고객 ID 기반 sessionId*가 *유출*되면 *영구 영향* (세션 단위는 *시간 지나면 자연 만료*인데 고객 단위는 *평생*)
5. **요약 전략 *필수*** — 슬라이딩 윈도우로 6개월 이력 처리 불가. RAG 또는 vector DB로 *과거 대화 indexing* 필요 (Round 4 PgVector와 결합).

### 권장 하이브리드 — *고객 단위 *요약* + 세션 단위 *원본***

- **세션 메모리** (현 Round 3): 최근 1상담 슬라이딩 (해당 대화 끝나면 *요약만* 저장)
- **고객 메모리** (장기): *요약 + 핵심 메타데이터* (선호·알레르기·과거 사고)
- 새 세션 시작 시 *고객 메모리의 요약*만 system message로 주입 → 토큰 비용 통제

> **사용자 답** (본인 운영 통찰 + Round 3 측정 + 기존 리서치 연결):
>
> ### 장점 — 개인화 관점에서 확실히 유리
>
> 이전 배달 상담 내역이 남아있게 되니까:
>
> 1. **이전 대화 내역 기반 *말투 다르게* 정하기** — 친근한 고객 vs 격식 있는 고객 톤 차등
> 2. **모니터링 시스템** 가능 — 누적 대화로 패턴 분석
> 3. **사용자 분류·판단 다르게**:
>    - *디지털 사용 어려운 사람* — 더 단순한 안내, 천천히 응답
>    - *악성 민원·반복 환불 고객* — 자동 escalation 또는 별도 정책 적용 (Q3 리서치의 *반복 환불 어뷰즈 탐지*와 연결)
>    - *VIP·단골 고객* — 우선 처리, 개인화 추천
>
> ### 리스크 — 4 방면 방어 대책 필요
>
> | 리스크 | 본인 통찰 | Round 3 측정·리서치 보강 |
> |---|---|---|
> | **데이터 탈취 위험** | 영속 = 평생 영향, 한 번 유출 시 회복 불가 | Q4 (1단계) 5단계 방어 패턴 — *영구 메모리*에서 더 critical |
> | **영속 유지 비용** | 추가적 인프라 비용 | Q1 (3단계) PostgreSQL vs Redis 비교, JDBC 도입 시 TTL/파티셔닝/암호화 |
> | **데이터 많이 쌓일수록 환각** | 누적 데이터가 LLM 추론 오염 | Q1 (2단계) *Lost in the Middle* (arxiv 2307.03172) + 환각 인과 분석 (2510.20229) — *길이가 아닌 컨텍스트 의존 누적이 원인* |
> | **타인 데이터 참고 + 개인정보 노출** | 다른 사람 데이터 참고 안 하게, 가져오지도 않게 | Q2 (1단계) CVE-2026-41712 + Q4 (1단계) owner 바인딩 (BOLA mitigation) + GDPR 삭제권 |
>
> ### 방어 대책 — Round 5 이전·이후 분리
>
> #### Round 5 이전부터 적용
> 1. **owner 바인딩** — `(userId ↔ conversationId)` 매핑 검증 (BOLA mitigation, Q4 1단계 프로덕션 Top 3)
> 2. **TTL + 파티셔닝** — *영속 유지 비용* 통제 (예: 90일 슬라이딩 + 분기별 cold storage 이관)
> 3. **요약 전략 (LLM-judge·structured slot)** — *데이터 누적 환각* 차단. Q1 (2단계) 핵심 논문 검증
>
> #### Round 5 도입 시 필수
> 4. **GDPR 삭제권** — *잊혀질 권리* 자동 처리 API. Round 3 in-memory는 자동 휘발이라 자연 회피지만 영구화 시 필수
> 5. **PII 마스킹 + 컬럼 암호화** — 전화·주소·결제정보 평문 저장 금지 (5주차 Guardrail과 결합)
> 6. **고객 단위 *요약* 보존** — 원본 대화 대신 *압축된 요약*만 (토큰 폭증 + 환각 둘 다 완화)
> 7. **시간 가중치** — 옛 정보(주소·선호) *최신성 가중* 적용 → *stale 정보 사고* 방지
>
> ### 권장 하이브리드 (Round 4·5 진화 경로)
>
> | 메모리 층 | 보존 데이터 | 가치 |
> |---|---|---|
> | **세션 메모리** (현 Round 3) | 최근 상담 turn (in-memory) | 지시 대명사·맥락 해결 |
> | **고객 메모리** (Round 5 본격) | *요약 + 핵심 메타데이터* (선호·알레르기·과거 사고·말투 정책) | 개인화·모니터링·분류 |
> | **사고·민원 보관** (Round 4 영속) | 법적 N년 보존 | 분쟁 추적·법무 |
>
> 새 세션 시작 시 *고객 메모리 요약*만 system message로 주입 → 토큰 비용 통제 + 환각 위험 ↓ + 개인화 ↑.
>
> ### 핵심 시사점
>
> - **개인화 유리** = 본인 통찰 정확. 산업 사례(Amazon Rufus·Shopify Sidekick·McKinsey 매출 10-15% ↑)도 같은 방향
> - **리스크 4 방면** = Round 3 measurement·기존 리서치와 *완벽 정합*
> - **방어 대책** = Round 3·4·5 단계 진화 경로로 분리. *영속화 *전*에 owner 바인딩·요약·시간 가중치 인프라 마련*이 핵심
> - GDPR 삭제권 = *Round 3 in-memory가 자동 회피*하지만 *Round 5 영구화 시 정면 충돌*. *지금부터 진화 경로에 포함*시키는 게 안전

---

## 부록 — Round 3 측정 데이터 요약 (이번 결정의 근거)

| 데이터 출처 | 값 |
|---|---|
| 1단계 100 trial 평균 응답시간 | T1 ~8s, T2 ~9s |
| 1단계 시나리오 4 세션 격리 | 20/20 (100%) |
| 2단계 10턴 토큰 (MAX=20) | 1204 → 3763 |
| 2단계 b 30턴 토큰 (MAX=20) | 1115 → 3704 (sweet spot) |
| 2단계 b 30턴 토큰 (MAX=2) | 1166 → 4262 (오히려 폭증) |
| 옛 정보 회수 (T8 MAX=20) | 10/10 |
| 응답-실재 분리 재발 사례 | trial별 다양 (가설 2 부분 부정) |

---


> QUEST 3단계 "설계 결정 문서" — InMemory vs JDBC 의사결정 트리 + 4 질문.
> EXPERIMENT_LOG_QUEST3 내용 추출 + 사용자 답 영역 명시.

---

## 1. 의사결정 트리 — 운영 조건별 저장소 선택

| 운영 조건 | InMemory | JDBC (h2:mem) | JDBC (h2:file) | PostgreSQL | Redis |
|---|:-:|:-:|:-:|:-:|:-:|
| 서비스가 로드밸런서 뒤 *멀티 인스턴스*로 뜨는가? | ❌ | ❌ | ❌ (file lock) | ✓ | ✓ |
| 서버 재시작 후에도 고객 대화가 이어져야 하는가? | ❌ | ❌ | ✓ | ✓ | ✓ (AOF/RDB) |
| 법적/감사 이유로 상담 이력을 *N년 보관*해야 하는가? | ❌ | ❌ | △ (백업 정책 필요) | ✓ | △ (장기 보관 비추천) |
| 단일 인스턴스 + 세션이 *분 단위로 짧은가*? | **✓** | ✓ | △ (오버) | △ (오버) | △ (오버) |
| H2 Console로 SQL 디버깅 가능한가? | ❌ | ✓ | ✓ | (PgAdmin) | (redis-cli) |
| 백업·HA·복제 기본 제공? | ❌ | ❌ | ❌ | ✓ (WAL, replica) | ✓ (replica) |
| 응답 latency 가장 짧은가? | ✓ (μs) | ✓ (μs) | △ (ms, disk I/O) | △ (네트워크) | ✓ (ms, in-memory) |

→ **단일 정답 없음**. 조건 조합에 따라 선택.

---

## 2. InMemory로 충분한 3가지 조건

1. **단일 인스턴스 + 짧은 세션** — 분 단위로 끝나는 챗봇, 재시작 시 잃어도 UX 영향 미미
2. **개발/데모/POC** — 빠른 부팅 (0.8초 vs JDBC 2-4초), 설정 0, 의존성 0
3. **테스트 자동화** — 매 테스트마다 *fresh state* 필요. 외부 DB 의존성 없음 (CI 환경 단순화)

**추가 조건 (덜 명확하지만 유효)**:
- 트래픽이 작아서 *세션 누적 자체*가 OOM 부담 안 됨 (TTL 없어도 OK)
- 감사·분석 요구 없음 (대화 이력 자체가 가치 X)

---

## 3. JDBC가 필요한 3가지 조건

1. **재시작 후 이어짐** — 배포·크래시·점검 시 *고객 상담 도중* 대화 보존. 배달 운영 표준.
2. **멀티 인스턴스** — 로드밸런서 뒤 N개 인스턴스. *같은 sessionId가 다른 인스턴스로 가도* 일관된 대화. *공유 저장소 필수*.
3. **감사/추적** — 운영팀이 *"고객이 그날 봇한테 뭐라고 했는지"* 사후 조회. 법적/CS 분쟁 자료. 90일~7년 보관 정책.

**추가 조건**:
- 회원제 서비스 (sessionId가 *고객 ID와 1:1*이라 영속 의미)
- 운영팀 인사이트 분석 (대화 패턴 → CS 개선 → BI 도구로 SQL 분석)

---

## 4. 배달 *실제 운영*이라면? — DB 선택

### 후보 비교

| DB | 장점 | 단점 | 배달 적합도 |
|---|---|---|---|
| **PostgreSQL** | • Round 4에서 *PgVector* 띄움 → 같은 DB 인스턴스 공유 가능<br>• 표준 SQL + 인덱스·파티셔닝·암호화 풍부<br>• WAL 백업 + replica HA | 트래픽 매우 클 때 write throughput 한계 (대화는 *write-heavy*) | ⭐⭐⭐⭐⭐ |
| **MySQL** | PostgreSQL과 비슷한 운영 부담, write 더 빠른 케이스 | Spring AI 1.0 starter는 PostgreSQL·MariaDB 우선. MySQL은 schema 직접 작성 | ⭐⭐⭐⭐ |
| **Redis** | 빠른 응답 latency, AOF 영속 | 대규모 영속 데이터엔 비효율, 감사 분석 어려움, 복잡한 검색 X | ⭐⭐⭐ (캐시 layer로 좋음) |
| **DynamoDB** | AWS 환경에선 무한 스케일 + 운영 부담 0 | 베andor lock-in, 복잡한 검색·집계 어려움, on-demand 비용 예측 어려움 | ⭐⭐⭐ |

### 추천 — **PostgreSQL** (가장 합리적)

**핵심 근거**:
1. **Round 4 PgVector와 같은 인스턴스 공유** — DB 두 개 운영 안 해도 됨. 운영 부담 ↓.
2. **표준 + 풍부한 운영 도구** — 인덱스·파티셔닝·column 암호화·WAL 백업 모두 표준.
3. **JdbcChatMemoryRepository auto-config 완벽 지원** — Spring AI 1.0 starter가 PostgreSQL schema 기본 제공.
4. **분석 친화** — BI 도구·SQL 쿼리로 *대화 패턴 분석* 가능. CS 개선에 직접 활용.

**하이브리드 (운영 규모 큼)**:
- *최신 활성 세션* → Redis (latency)
- *완료된 세션 영속화* → PostgreSQL (감사·분석)
- *세션 종료 시* Redis → PostgreSQL 이관 (배치 또는 stream)

> **사용자 답** (단일 PostgreSQL + pgvector 통합, 옵션 1):
>
> **선택: PostgreSQL 단일 인스턴스 + pgvector extension** — chat memory (Round 3 `JdbcChatMemoryRepository`) + 도메인 RDB + vector store (Round 4 `PgVectorStore`) *모두 한 DB*.
>
> ### 4축 평가 — PostgreSQL이 1순위
>
> | 평가 축 | 1순위 | 비고 |
> |---|---|---|
> | **1. Round 3·4·5 일관성** | PostgreSQL + pgvector | Round 4 발제 명시(PgVector), Round 3 memory → Round 4 vector → Round 5 비기능까지 **마이그레이션 1회**. Round 5 RLS·암호화·백업·감사·파티셔닝 비기능 요구도 PostgreSQL 1급 지원 |
> | **2. 비용** | PostgreSQL + pgvector | 자가호스트 $0, Neon·Supabase 무료 티어 충분. Pinecone $50/mo+는 학습 단계 과잉 |
> | **3. 운영 부담** | PostgreSQL + pgvector | DB 1개 · 백업·모니터링 일원화 · *이중 쓰기 일관성 없음*. 전용 DB 추가 = 학습 비용 ↑ |
> | **4. 학습 친화도** | PostgreSQL + pgvector | SQL·트랜잭션·인덱스 등 기존 지식 재사용. Spring AI `PgVectorStore` 자동 구성 |
>
> ### 패턴 A — 단일 PostgreSQL 통합 (채택)
>
> ```
> PostgreSQL 단일 인스턴스
> ├─ chat_memory  (Round 3, JdbcChatMemoryRepository — 이미 검증)
> ├─ menu/faq/order  (도메인 RDB)
> └─ vector_store  (Round 4, pgvector HNSW — RAG embedding)
>         ↑ Spring AI (PgVectorStore + JdbcChatMemoryRepository)
> ```
>
> 장점:
> - 마이그레이션 1회 (Round 3 h2:file → Round 4 PostgreSQL로 한 번만)
> - 백업·RLS·암호화 통합 (Q5 비기능 요구 7개 모두 PostgreSQL 1급 지원)
> - 트랜잭션 일관성 (memory + vector 동시 write도 ACID)
> - 네트워크 홉 0 (전용 DB 분리 시 추가 latency)
>
> 한계 (한계 시점):
> - 수직 확장만 (10M+ 벡터 시 패턴 B로 분리 검토)
> - 배달 챗봇 규모(메뉴 수천~수만, FAQ 수백, 주문 누적 수만~수십만)에선 **사실상 도달 불가**
>
> ### 벤치마크 (1M 스케일 기준)
>
> | DB | latency p50 | QPS |
> |---|---:|---:|
> | pgvector (HNSW) | **5~8ms** | ~ |
> | Qdrant | ~ | ~1840 |
> | Pinecone | ~300ms (네트워크 포함) | ~ |
> | Chroma (소규모) | ~30ms | ~ |
>
> → 배달 챗봇 규모에서 **pgvector가 전용 DB와 동등 또는 우위**.
>
> ### 추가 근거 — PostgreSQL vs MariaDB 비교 (둘 다 RDB + vector 지원하는데 왜 PostgreSQL?)
>
> | 차원 | PostgreSQL + pgvector | MariaDB 11.7+ Vector |
> |---|---|---|
> | **Vector extension 출시** | **2021** (4년 성숙) | **2024-11** (1년 미만, 매우 신규) |
> | **인덱스 유형** | HNSW + IVFFlat (선택) | HNSW만 |
> | **벤치마크 데이터** | 풍부 (pgvector vs Pinecone·Qdrant·Weaviate 모두 측정) | 거의 없음 (신규) |
> | **Spring AI 1.0 지원** | ✓ `PgVectorStore` 자동 구성 + 튜토리얼 풍부 | ✓ `MariaDBVectorStore` — 튜토리얼 부족 |
> | **하이브리드 검색** (BM25 + vector) | pgvector + PostgreSQL FTS 결합 | 미지원 (별도 구현 필요) |
> | **산업 채택** | Supabase·Neon·Crunchy Data·OpenAI·Anthropic 인프라 | 사례 검색 안 됨 |
> | **운영 도구** | pg_partman·WAL archiving·streaming replication·logical replication (vector 워크로드 사례 풍부) | mariabackup·galera 성숙하나 *vector 사례 부족* |
> | **커뮤니티 자료** | StackOverflow·dev.to·튜토리얼 압도적 | 신규라 자료 적음 |
> | **Round 4 발제 정합** | **명시** ("PgVector + 문서 임베딩 + QuestionAnswerAdvisor") | 미언급 |
>
> **한 줄 결론**: *PostgreSQL = 4년 성숙 + 검증 벤치마크 + 발제 명시 / MariaDB = 신규(1년 미만) + 자료·사례 부족*. 기술적으론 둘 다 가능하지만 *학습 시간 비용*과 *Round 4 정합성* 측면에서 PostgreSQL이 합리적 default.
>
> ### 단계 진화 경로
>
> | Round | DB 상태 |
> |---|---|
> | **Round 3 (현재)** | h2:file (영속성 검증 완료) |
> | **Round 4** | PostgreSQL + pgvector 마이그레이션 + 도메인 RDB 추가 + RAG vector store |
> | **Round 5** | 같은 PostgreSQL 인스턴스 + 비기능 (RLS·암호화·백업·감사 로그·파티셔닝·자동 삭제) |
>
> **마이그레이션 1회**로 라운드 진화 완성. 분리 옵션(패턴 B·C)은 *측정된 병목 (>10M 벡터, > 1K QPS) 발생 시 단계적 도입*.
>
> ### 핵심 시사점
>
> - 발제(PgVector 명시) + 학습 친화도 + 비용 + 운영 부담 *4축 모두* PostgreSQL 우위
> - MariaDB가 *기술적으로 안 되는* 건 아님 — 단 *학습 시간 비용*과 *주변 자료* 측면에서 PostgreSQL이 가성비 ↑
> - Q5 비기능 요구 7개와 *완벽 정합* (Round 3·4·5 단계 진화)
>
> **출처**:
> - [Spring AI Vector Database 지원 목록](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)
> - [pgvector GitHub (2021~)](https://github.com/pgvector/pgvector)
> - [Vector DB 벤치마크 2026 (Reintech)](https://reintech.io/blog/vector-database-comparison-2026-pinecone-weaviate-milvus-qdrant-chroma)
> - [pgvector vs Qdrant 운영 비교 (Tigerdata)](https://www.tigerdata.com/blog/pgvector-vs-qdrant)
> - [Supabase pgvector 모듈](https://supabase.com/modules/vector)
> - [Why AI Startups Choose PostgreSQL (Medium)](https://medium.com/@takafumi.endo/why-ai-startups-choose-postgresql-supabase-neon-pgvector-7d1e1383b3dd)
> - [pgvector vs Pinecone 운영 비교 (Encore)](https://encore.dev/articles/pgvector-vs-pinecone)
> - [Hybrid Search BM25 + Vector (Digital Applied)](https://www.digitalapplied.com/blog/hybrid-search-bm25-vector-reranking-reference-2026)
> - [Crunchy Data pgvector 가이드북](https://www.crunchydata.com/blog/data-encryption-in-postgres-a-guidebook)

---

## 5. JDBC 도입 시 동시 고려할 비기능 요구 3가지

### 1. 인덱스 — 필수

`schema-h2.sql` 또는 PostgreSQL schema에 *`(conversation_id, "timestamp")` 복합 인덱스* 필수.
- 이유: SessionController `get(sessionId)` 가 *세션별 시간순* 조회. 인덱스 없으면 *full table scan*.
- 대규모 (수십억 행) 시: *partial index* 또는 *partition 단위 인덱스* 고려.

### 2. TTL / 파티셔닝 — 누적 통제

대화 이력은 *write-heavy* + *시간 지나면 가치 ↓*.

- **PostgreSQL 권장**: `pg_partman` 같은 *시간 파티셔닝* 도구. 월 단위 partition. 6개월 이전 partition을 *cold storage* (S3 등)로 이관.
- **단순 정책**: 90일 이전 세션 *자동 삭제* 배치 (`DELETE WHERE timestamp < NOW() - INTERVAL '90 days'`).
- **법적 N년 보관** 시: 삭제 대신 *archive 테이블*로 이관.

### 3. 암호화 — 개인정보 보호

`content` 필드가 *PII 평문*. 다음 중 하나 이상:

- **Column 레벨 암호화** (PostgreSQL `pgcrypto`)
- **DB 레벨 TDE** (Transparent Data Encryption — AWS RDS, GCP Cloud SQL 지원)
- **저장 전 마스킹** (5주차 Guardrail) — *전화번호·카드번호 정규식*으로 `****` 치환

**최소 보안**: DB 계정 분리 — *상담원 조회용 read-only 뷰*만 노출. *write 권한*은 app 계정만.

---

## 부록 — Round 3 측정으로 본 *함정 4가지* (EXPERIMENT_LOG_QUEST3 인용)

### 함정 1 — `h2:mem`을 *영속*으로 착각

`DB_CLOSE_DELAY=-1`은 *JVM 종료 전까지 DB 안 닫음*. *JVM 자체 죽으면 사라짐*. **"영속화" = JVM 외부 저장**. h2:mem은 영속 아님.

### 함정 2 — `initialize-schema: embedded` ≠ h2:file

*embedded* 분류는 *JVM 메모리에 있는지* 기준. h2:file은 *파일 영속*이라 *embedded 미분류*. **`always`가 안전한 default**.

### 함정 3 — Spring AI 1.0 H2 schema 미포함

PostgreSQL·MySQL·MariaDB는 제공. **H2는 직접 작성 필수** (`classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql`).

### 함정 4 — `pkill -f BaedalSupportApplication` 광범위 매칭

JDBC 8081 죽이려고 호출했더니 *main 8080 bootRun까지 죽임*. **PID 명시 `kill $(lsof -ti tcp:PORT)` 가 default 원칙**.

---

## 부록 — Round 3 측정 영속성 검증 표 (EXPERIMENT_LOG_QUEST3에서 인용)

| 저장소 설정 | 재시작 후 Memory 유지? | 비고 |
|---|:-:|---|
| InMemory (기본) | ❌ | 1단계 측정에서 검증 |
| `jdbc:h2:mem:...` | ❌ | 2026-05-31 16:47 검증 |
| **`jdbc:h2:file:./data/baedal-jdbc`** | **✓** | 2026-05-31 17:20 검증 (세션 1개·메시지 2개 모두 보존) |

> **사용자 답** (C — 균형·단계적 진화, 라운드별 우선순위):
>
> **선택: 인덱스/TTL/암호화 *3개 한 라운드에 다*가 아니라, *라운드별 단계 도입*** — 학습 + 산업 표준 둘 다 만족.
>
> ### 7 비기능 요구 종합 (3개 → 7개로 확장)
>
> | # | 요구 | Round | 성격 |
> |---|---|---|---|
> | 1 | **인덱스** `(conversation_id, created_at)` 복합/BRIN | **Round 3** | 운영 권고 |
> | 2 | **TTL / 파티셔닝** (pg_partman, 30~90일) | Round 3 stub → Round 5 | 운영 + **법적 (파기)** |
> | 3 | **백업 / HA** (pg_basebackup + WAL, replication) | Round 5 | 운영 권고 |
> | 4 | **암호화** (at-rest TLS + 컬럼 pgcrypto) | Round 4-5 | **법적 의무** (GDPR Art.32, KISA) |
> | 5 | **접근 제어 / RLS** (conversationId↔userId + Row Level Security) | Round 4 | 법적 + 운영 |
> | 6 | **감사 로그** (최소 1년·민감 2년·WORM) | Round 5 | **법적 의무** (한국 개인정보보호법) |
> | 7 | **모니터링/관측** (pg_stat, slow query, memory poisoning 회귀) | Round 4-5 | 운영 권고 |
>
> ### Round 3 우선순위 (현재)
>
> | 순위 | 요구 | 적용 |
> |---|---|---|
> | ① | **conversationId 격리** (접근 제어 stub) | 1단계 Q3·Q4 결정과 정합. 측정 가능 (시나리오 4 격리 100%) |
> | ② | **인덱스** `(conversation_id, created_at)` | JDBC 도입 시 즉시. `schema-h2.sql`에 포함됨 (이미 적용) |
> | ③ | **TTL 인터페이스 stub** | 30일 정책 명시. 구현은 Round 5 |
>
> *암호화·HA·감사 로그는 Round 4·5로 이월* (Round 3는 학습 + 측정 가능 영역에 집중).
>
> ### Round 4 우선순위 (Tool Calling + DB)
>
> | 순위 | 요구 | 적용 |
> |---|---|---|
> | ① | 접근 제어 / RLS | Tool이 DB 접근하면 공격 표면 폭발 — *prompt injection 방어 표면도 함께* |
> | ② | prompt injection 방어 | OWASP LLM01. allowlist + output filter |
> | ③ | 암호화 (민감 컬럼) | pgcrypto. PII 평문 저장 차단 |
> | ④ | 모니터링 | memory poisoning 회귀 테스트 |
>
> ### Round 5 우선순위 (프로덕션 운영화)
>
> | 순위 | 요구 | 적용 |
> |---|---|---|
> | ① | 백업/PITR + HA replication | pg_basebackup + WAL archiving + streaming replication |
> | ② | 감사 로그 1-2년 보존 (WORM/append-only) | 한국 개인정보보호법 + 분쟁조정 |
> | ③ | 파티셔닝 (pg_partman 시간 기반) | 100M rows 또는 일 +200K 행 시점 |
> | ④ | GDPR 30일 자동 삭제 워크플로 | 잊혀질 권리 cascade |
>
> ### 법적 의무 vs 운영 권고 (분리 인식)
>
> | 법적 의무 (선택 불가) | 운영 권고 (trade-off 영역) |
> |---|---|
> | **암호화** (비밀번호 해시·민감정보 블록암호 — KISA 안내서, GDPR Art.32, PCI DSS) | 인덱스 선택 (B-tree/BRIN/partial) |
> | **감사 로그 최소 1년**, 5만명↑·민감 2년 (한국 개인정보보호법) | 파티셔닝 시점 (100M rows 임계) |
> | **GDPR Art.17 — 30일 내 삭제** (요청 받으면 cascade 전파) | HA 토폴로지 (sync 1 + async N 조합) |
> | 보관 기간 (§21 — 결제 5년·분쟁 3년, 별도 법령 시 분리 저장) | 백업 전략 (pg_basebackup vs pg_dump) |
>
> ### 핵심 시사점
>
> - **AI draft 3개 → 산업 표준 7개로 확장** — *법적 의무*가 *3개 중 2개* (감사 로그·삭제권) 추가
> - **라운드별 단계 도입**이 학습 + 산업 표준 둘 다 만족
> - Round 3는 *측정 가능 NFR* (인덱스·격리) 집중 — Round 2 패턴 (10회 반복 측정)과 정합
>
> **출처**:
> - [PostgreSQL Write-Heavy Workload Best Practices (Cloudraft)](https://www.cloudraft.io/blog/tuning-postgresql-for-write-heavy-workloads)
> - [pg_partman Time-based Partitioning (Percona)](https://www.percona.com/blog/postgresql-partitioning-made-easy-using-pg_partman-timebased/)
> - [PCI DSS 4.0.1 Encryption Requirements](https://www.tripwire.com/state-of-security/pci-dss-4-protect-stored-account-data-and-protect-cardholder-data)
> - [한국 개인정보보호법 보존·파기](https://www.lawnb.com/Info/ContentView?sid=L006FBC03E307C6F)
> - [GDPR Art.32 — Security of Processing](https://gdpr-info.eu/art-32-gdpr/)
> - [KISA 개인정보 안전성 확보 안내서](https://www.kisa.or.kr/2060301/form?postSeq=2&lang_type=KO&page=1)
> - [OWASP LLM01 Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)
> - [Anthropic 5-Year Data Retention Update](https://www.theregister.com/2025/08/28/anthropic_five_year_data_retention/)
> - [LangChain PostgresChatMessageHistory](https://hexacluster.ai/blog/postgres-for-chat-history-langchain-postgres-postgreschatmessagehistory)
