# Round 1 Quest 3 — Streaming 측정 결과

시나리오: "주문번호 2024-1234 배달 어디쯤에 있어요?"

| 엔드포인트 | TTFB(첫 바이트=체감 시작) | TOTAL | 형태 |
|---|---|---|---|
| 동기 `/api/v1/chat` | 15.94s | 15.94s | 전체 생성 후 1회 출력(347B) |
| 스트리밍 `/api/v1/chat/stream` | **1.42s** | 5.66s | SSE `data:` 토큰 단위 160청크(858B) |

핵심:
- **체감 시작점 ≈ 11배 단축** (15.9s → 1.4s). 사용자가 "응답 없음"으로 느끼는 임계 3초 이내 진입.
- 토큰 단위 스트림 확인: `data:배` `data:송` `data: 상태` … → 타이핑되듯 출력(체크포인트 #3 충족).
- TOTAL 차이(15.9 vs 5.7s)는 응답 길이 + 동기 `ChatController`가 SYSTEM_PROMPT 미적용(맨 빌더)이라 출력이 달라 직접 비교 부적절 → 비교 핵심 지표는 **TTFB**.

구현: `StreamingChatController.chatStream()` = `builder.defaultSystem(SYSTEM_PROMPT).build().prompt().user(...).stream().content()` (Flux<String>, `TEXT_EVENT_STREAM_VALUE`). webflux 의존성 기존 존재.

채점 문서(사용자, 마지막 일괄):
- [ ] 모든 엔드포인트에 스트리밍을 적용해야 하는가?
- [ ] `/api/v1/support`(Structured Output)에 `.stream()` 쓰면 어떤 문제? (JSON DTO를 토막내 보내면 파싱 불가 → 구조화 출력과 스트리밍의 충돌)
- [ ] 프로덕션 스트리밍 적용 시 프론트엔드 변화(SSE 수신·점진 렌더)
- [ ] 동기 ChatController의 SYSTEM_PROMPT 미적용 비대칭을 맞출지
