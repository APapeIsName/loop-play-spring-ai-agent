package com.baedal.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

// Structured Output DTO. 각 필드 규칙은 @JsonPropertyDescription으로 모델에 전달.
public record SupportResponse(
        @JsonProperty(required = true)
        @JsonPropertyDescription("상담 핵심 요약 1~2문장(내부 트리아지용). 고객 감정을 먼저 인지해 반영")
        String summary,

        @JsonProperty(required = true)
        @JsonPropertyDescription("고객에게 그대로 보낼 답변문. 존댓말, 감정을 먼저 인지, 3문장 이내. needsHumanHandoff=true면 전문 상담사 연결 안내 문장을 포함")
        String replyToCustomer,

        @JsonProperty(required = true)
        @JsonPropertyDescription("여러 분류에 걸치면 고객의 최종 목적(케이스를 종결시키는 결과)으로 택1. 예: 취소+환불 → REFUND. 안전·결제 위험 섞이면 위험 큰 쪽 우선 (시스템이 판단)")
        Category category,

        @JsonProperty(required = true)
        @JsonPropertyDescription("긴급도. CRITICAL=건강·안전·법적위협, HIGH=금전손실·강한불만·시간임박, NORMAL=일반문의, LOW=단순확인")
        Urgency urgency,

        @JsonProperty(required = true)
        @JsonPropertyDescription("답이 시스템에서 확인된 값에 근거하면 VERIFIED, 확인 없이 추정·일반지식으로 답하면 INFERRED. 현재 시스템 조회 경로가 없으므로 항상 INFERRED")
        AnswerBasis answerBasis,

        @JsonProperty(required = true)
        @JsonPropertyDescription("판단 근거 출처를 짧게. 시스템 조회 불가 시 '고객 진술' 등으로 표기")
        String basisReference,

        @JsonProperty(required = true)
        @JsonPropertyDescription("true 조건: 안전·건강·법적 위협, 보상/환불 금액 약속 필요, 봇 권한 밖, 정보 모순·불충분으로 확신 불가. 그 외 false")
        boolean needsHumanHandoff,

        @JsonProperty(required = true)
        @JsonPropertyDescription("needsHumanHandoff=true면 사람에게 넘기는 사유(라우팅·기록용), false면 빈 문자열")
        String handoffReason,

        @JsonProperty(required = true)
        @JsonPropertyDescription("상담/시스템 측이 취할 다음 조치(예: 주문 조회, 담당 이관)")
        String agentNextStep,

        @JsonProperty(required = true)
        @JsonPropertyDescription("고객이 해야 할 행동(예: 사진 첨부, 음식 보관). 없으면 빈 문자열")
        String customerAction,

        @JsonProperty(required = true)
        @JsonPropertyDescription("처리에 필요하나 아직 못 받은 정보 목록(예: 주문번호). customerAction과 중복 금지")
        List<String> neededInfo
) {
    public enum Category    { ORDER, DELIVERY, REFUND, PAYMENT, ETC }
    public enum Urgency     { LOW, NORMAL, HIGH, CRITICAL }
    public enum AnswerBasis { INFERRED, VERIFIED }
}
