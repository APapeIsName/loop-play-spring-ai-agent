# Round 1 — 제출 통합 초안 (데이터/진행 정리)

> 본 문서: **사실·데이터·진행상황만** 정리(AI 작성).
> `[✍️ 사용자]` 표시 = **채점 핵심 "왜/근거"** — 사용자가 최종 작성.
> 상세 원본: 같은 폴더의 round1-design-decisions / quest2-summary / quest2-ambiguous-summary / quest3-streaming / *.jsonl

## 진행 현황

| Quest | 코드 | 검증 | 상태 |
|---|---|---|---|
| 1 System Prompt + Structured Output | BaedalPrompt(B안), SupportResponse(11필드+@JsonProperty/Description), SupportController(발제예시) | 시나리오3종 #1·#2·핸드오프 | ✅ 완료 |
| 2 Prompt Lab 정량비교 + 실패관찰 | PromptLabController(temp 필드·samples 추가) | 39잡 + 모호 재실험 6잡, 전부 OK | ✅ 데이터 완료 |
| 3 Streaming | StreamingChatController(.stream().content()) | sync vs stream 측정 | ✅ 완료 |
| 4 Observability + AI리뷰 | PerformanceLoggingAdvisor(+SimpleLoggerAdvisor) | advisor 토큰·시간 / 2배토큰 1.19x / 관찰성 원인규명 | ✅ 데이터 완료 |

---

## Quest 1 — 데이터

- `SupportResponse` 최종 11필드: summary, replyToCustomer, category, urgency, answerBasis, basisReference, needsHumanHandoff, handoffReason, agentNextStep, customerAction, neededInfo. `@JsonPropertyDescription`(필드 규칙) + `@JsonProperty(required=true)`.
- `Category` enum 5개 **유지**(ORDER/DELIVERY/REFUND/PAYMENT/ETC).
- 검증 #1(L1b 전): answerBasis/basisReference/urgency/customerAction이 S2·S3에서 **null**.
- 검증 #2(L1b 후): 11필드 전부 충전, S2 분류 ORDER→**REFUND** 교정.
- 검증 #2b: 안전위협 시나리오 → `urgency=CRITICAL`, `needsHumanHandoff=true` 적중. (단 `handoffReason` 빈값 — 모델 하위규칙 미준수)
- 시나리오 3종 응답 JSON 원본: round1-design-decisions.md §8/§9, jsonl.

**채점 작성 항목:**
- `[✍️ 사용자]` 추가 필드들을 **왜** 넣었는가 (필드별 선택 근거)
- `[✍️ 사용자]` `Category` 5개로 **왜 충분**한가 / 보상·위생 공백은? (S3 보상건 DELIVERY 분류가 근거 데이터)
- `[✍️ 사용자]` System Prompt `[금지]` **왜 그 3개**인가

---

## Quest 2 — 데이터

- T1a 단순 vs 구조화 (시나리오 "배달 어디쯤", repeat5): **둘 다 consistency 1.0** (DELIVERY).
- T1 실패관찰 [금지]有/無 (공격 3종): 치명적 유출 없음(qwen2.5 자체 안전). 가장 뚜렷한 차이 = A2 경쟁사 비교에 [금지]無가 응함. 원문 quest2-summary.md.
- T2 temperature 0.0~1.0(0.1×11, repeat5): **전부 1.0**.
- T3 repeat 1~10: **전부 1.0**.
- T4 강건성(10변형): 9개 1.0 + 존댓말 5/5, **예외 日本語 0.8(ORDER 오분류)**.
- 모호 재실험("취소+환불", repeat10): simple·struct·temp 전부 **1.0 REFUND** → 진단: **L1b의 category 설명("취소+환불→REFUND")이 분류를 결정. `@JsonPropertyDescription`은 prompt 무관 항상 주입 → 단순 vs 구조화 비교 교란.**
- **SYSTEM_PROMPT 수정 전/후 비교**(promptmod, "라이더 보상" 시나리오, repeat5; 원본 round1-quest2-promptmod-summary.md): baseline(B안)·수정안 **둘 다 categoryConsistency 1.0**(무변별) — 그러나 **categoryCounts가 DELIVERY 5/5 → REFUND 5/5 로 이동**. 메타관찰: *categoryConsistency는 "프롬프트 내부 안정성"이라 프롬프트 간 차이를 못 잡는다 → 진짜 효과는 categoryCounts 분포에서 보임.* 수정 [금지]("보상·환불·재배달 임의 약속 금지")가 보상 문의를 DELIVERY→REFUND로 재프레이밍.

**채점 작성 항목:**
- `[✍️ 사용자]` temperature 0.3 근거 (데이터: 분류 일관성은 temp 무관 1.0 → 근거는 응답 *문장* 변동성 쪽)
- `[✍️ 사용자]` 구조화 vs 단순 우열 + **어떤 상황에서 단순이 나은가** (+ 포화·L1b 교란 한계 언급)
- `[✍️ 사용자]` [금지] 제거 시 프로덕션 사고 **3가지+** (데이터: 경쟁사 응답·분류 흔들림)
- `[✍️ 사용자]` 실패 관찰 해석(공격 3종 [금지]有/無 응답 인용)

---

## Quest 3 — 데이터

- 동기 `/api/v1/chat` TTFB=15.94s / TOTAL 15.94s.
- 스트리밍 `/api/v1/chat/stream` TTFB=**1.42s** / TOTAL 5.66s, SSE 토큰 160청크.
- 체감 시작 ≈ **11배 단축**. 토큰 단위 출력 확인(체크포인트 #3).

**채점 작성 항목:**
- `[✍️ 사용자]` 스트리밍을 모든 엔드포인트에 적용해야 하나
- `[✍️ 사용자]` `/api/v1/support`(Structured Output)에 `.stream()` 쓰면 문제 (JSON DTO 토막 → 파싱 불가)
- `[✍️ 사용자]` 프로덕션 스트리밍 시 프론트 변화 / 동기 ChatController SYSTEM_PROMPT 비대칭 처리

---

## Quest 4 — 데이터 (검증 완료)

- 구현: `PerformanceLoggingAdvisor.adviseCall()`(시간+토큰, null 방어) + `SupportController`에 `.defaultAdvisors(performanceAdvisor, new SimpleLoggerAdvisor())` 등록 + `PromptLabController`에 performanceAdvisor 등록(임의 프롬프트 토큰 측정용).
- ✅ 응답시간·토큰 로깅: 예) `LLM 호출 완료 — 26096ms | 입력 1205 | 출력 169 | 총 1374`.
- ✅ **관찰성 원인 규명**: `application.yml`의 `org.springframework.ai: DEBUG`는 정상. Spring AI **1.0 GA는 그것만으론 프롬프트 전문을 안 찍음** — 내장 `SimpleLoggerAdvisor` 등록해야 `DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor : request: ChatClientRequest[prompt=Prompt{messages=[SystemMessage{...` 로 전문 노출됨(등록 후 12건 확인). 발제 예시(`o.s.a.c.c.ChatClient : Prompt:`)는 구 마일스톤 기준.
- ✅ **System Prompt 2배 입력토큰**: 1204 → 1436 (**1.19x**, 2배 아님). 이유: `.entity()`가 11필드 JSON 스키마를 프롬프트에 주입 → 시스템 프롬프트는 입력의 일부일 뿐, 2배 해도 전체 ~19%만 증가.
- ✅ 시나리오 3종 분류: S1 DELIVERY / **S2 REFUND** / S3 DELIVERY, urgency 전부 NORMAL.
- ✅ 민감정보: git 추적 파일/`application.yml`에 키·비밀번호 없음(Ollama 로컬, API Key 불필요).

**채점 작성 항목:**
- `[✍️ 사용자]` AI 코드 리뷰(AI에 "Spring AI 배달 챗봇" 요청 → 프로덕션 결함 3개 + 개선)
- `[✍️ 사용자]` 관찰성/2배토큰/분류 데이터 **해석**(왜 1.19x인지, S3가 왜 DELIVERY인지 등)

## QUEST 자가점검 — 전체 체크리스트 상태

범례: ✅ 검증완료(코드/행동) · ✍️ 사용자 작성(채점) · ⚠️ 확인 필요

**1단계**
- ✅ build/bootRun 성공 + `/api/v1/support` 정상 응답
- ✅ System Prompt 4섹션 분리([역할]/[규칙]/[금지]/[응답 포맷], B안)
- ✅ 시나리오 3종 category 변별(S1 DELIVERY / S2 REFUND / S3 DELIVERY) — urgency 균일(NORMAL)
- ✍️ SupportResponse 추가 필드 **선택 근거**

**2단계**
- ✅ 단순 vs 구조화 categoryConsistency 수치 기록(둘 다 1.0 + L1b 교란 원인규명)
- ✅ [금지] 제거 후 공격 3종 응답 원문 기록(quest2-summary)
- ✍️ 프로덕션 배포 시 예상 사고 3가지+
- ✅ temperature 데이터 확보 / ✍️ 그 데이터로 0.3 **선택 근거** 작성
- ✅ **SYSTEM_PROMPT 수정 전/후 비교 완료**(promptmod): consistency 1.0/1.0 무변별, 단 categoryCounts DELIVERY→REFUND 이동 (←원래 ❌였던 항목 해소)
- ✍️ 실패 관찰 해석

**3단계**
- ✅ 글자 단위 스트리밍 확인(SSE 토큰 160청크)
- ✅ 동기 vs 스트리밍 체감차 기록(TTFB 15.9s→1.4s)
- ✍️ 스트리밍 적용 범위 판단(Structured Output 충돌 포함)

**4단계**
- ✅ PerformanceLoggingAdvisor 토큰·응답시간 출력
- ✅ System Prompt 2배 입력토큰 변화 기록(1204→1436, 1.19x)
- ✅ (관찰성) SimpleLoggerAdvisor로 프롬프트 전문 노출 원인규명
- ✍️ AI 생성 코드 문제 3개 + 개선방안

**공통 / PR 제출 체크**
- ✅ `./gradlew build`(test 제외) 컴파일 에러 없음
- ✅ 민감정보 없음(소스·application.yml, Ollama 로컬 무키)
- ✅ `.claude/` `.gitignore` 추가 완료(사용자 승인) / build·.gradle·.idea도 ignore
- ⚠️ 변경 파일 **21개**(>20): ignore 처리 후 초과분은 전부 의도적 `.private/`(12) — junk 아님, 제출 시 그대로/일부제외 판단
- ✍️ "내가 배운 것 / 의문점 / 다음 라운드 아이디어"
- ✍️ PR 제목 `[Round 1] {이름} - {단계}` / 본문 진행·막힌 곳 / 셀프리뷰

> 코드·검증 항목은 전부 ✅. 남은 ✍️ 항목 = 채점 핵심(사용자 작성). 데이터·원본은 이 폴더에 모두 보존.

## 공통 (채점)

- `[✍️ 사용자]` 내가 배운 것 / 의문점 / 다음 라운드 아이디어
