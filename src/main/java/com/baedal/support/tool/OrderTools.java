package com.baedal.support.tool;

import com.baedal.support.domain.Order;
import com.baedal.support.domain.OrderMockService;
import com.baedal.support.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 배달 상담 에이전트가 사용할 Tool 묶음.
 * <p>
 * 설계 원칙:
 * <ul>
 *     <li>@Tool의 {@code description}은 LLM이 읽는 "API 문서"다. 한국어로 명확히 작성한다.</li>
 *     <li>각 Tool은 실패 상황을 예외가 아닌 "결과 값"으로 표현한다.
 *         예외를 던지면 LLM이 Fallback할 기회를 잃는다.</li>
 *     <li>{@link #cancelOrder(String, String)}는 <b>멱등(idempotent)</b>하게 설계한다.
 *         이미 취소된 주문을 다시 취소 요청해도 동일한 성공 응답을 돌려준다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderMockService orderService;

    @Tool(description = """
            주어진 주문번호의 주문 내역(메뉴, 수량, 단가, 총 결제 금액, 주문 상태, 주문 시각, 예상 배달 완료 시각)을 조회한다.

            [언제 호출]
            - 고객이 메뉴, 수량, 결제 금액, 주문 시각 같은 구체적인 주문 내역을 물을 때.
            - 예: "뭐 시켰지?", "얼마였어요?", "콜라 같이 시켰던가?"

            [언제 호출하지 않음]
            - 배달 진행 상황, 라이더 위치, "어디쯤?", "어떻게 됐어요?"처럼 진행/도착을 묻는 발화는 getDeliveryStatus를 호출한다.
            - 주문번호가 발화에 없으면 호출하지 않는다.

            [입력]
            - orderId: "YYYY-XXXX" 형식. 예: "2024-1234"

            [실패 시]
            - 존재하지 않는 주문번호면 null을 반환한다. 예외를 던지지 않는다.
            """)
    public OrderDetailView getOrderDetail(
            @ToolParam(description = "조회할 주문번호. YYYY-XXXX 형식. 예: 2024-1234") String orderId) {
        log.info("[Tool] getOrderDetail(orderId={})", orderId);
        return orderService.findById(orderId).map(this::toDetailView).orElse(null);
    }

    @Tool(description = """
            주어진 주문번호의 현재 배달 상태와 라이더 위치를 조회한다.
            배달 중인 주문에 대해서만 라이더 위치가 반환되며,
            아직 배달이 시작되지 않았거나 이미 배달 완료된 주문은 상태만 반환된다.
            존재하지 않는 주문번호면 null을 반환한다.
            """)
    public DeliveryStatusView getDeliveryStatus(
            @ToolParam(description = "배달 상태를 조회할 주문번호. YYYY-XXXX 형식. 예: 2024-1234") String orderId) {
        log.info("[Tool] getDeliveryStatus(orderId={})", orderId);
        return orderService.findById(orderId).map(this::toDeliveryView).orElse(null);
    }

    @Tool(description = """
            주어진 주문번호의 주문을 취소한다. 결과는 항상 CancelOrderResult로 반환되며, outcome 필드로 처리 결과를 판단한다.

            [언제 호출]
            - 고객이 "취소해주세요", "취소할게요"처럼 명시적으로 주문 취소를 요청하고, 주문번호가 함께 제공된 경우에만 호출한다.

            [언제 호출하지 않음]
            - "취소 가능해요?", "취소되나요?"처럼 가능 여부/정책만 묻는 질문에는 호출하지 않는다.
            - 환불 정책 일반 문의에는 호출하지 않는다.
            - 주문번호가 발화에 없으면 호출하지 않는다.

            [입력]
            - orderId: "YYYY-XXXX" 형식. 예: "2024-1234"
            - reason: 고객이 말한 취소 사유의 자연어 요약. 예: "집 앞에 사람이 없어요"

            [반환되는 outcome 4종]
            - CANCELED: 이번 호출에서 정상 취소됨.
            - ALREADY_CANCELED: 이미 취소되어 있던 주문(멱등 처리 — 에러 아님).
            - NOT_CANCELABLE: 조리 시작 이후 등 자동 취소 불가. 상담원 연결 안내가 필요한 상태.
            - NOT_FOUND: 주문번호가 존재하지 않음.

            [중요]
            - 동일 주문을 두 번 취소 요청해도 예외를 던지지 않는다. 두 번째는 ALREADY_CANCELED로 동일한 정보를 돌려준다.
            """)
    public CancelOrderResult cancelOrder(
            @ToolParam(description = "취소할 주문번호. YYYY-XXXX 형식. 예: 2024-1234") String orderId,
            @ToolParam(description = "고객이 말한 취소 사유의 자연어 요약. 예: '집 앞에 사람이 없어요'") String reason) {
        log.info("[Tool] cancelOrder(orderId={}, reason={})", orderId, reason);

        Order order = orderService.findById(orderId).orElse(null);
        if (order == null) {
            return new CancelOrderResult(orderId, CancelOrderResult.Outcome.NOT_FOUND,
                    "해당 주문번호를 찾을 수 없습니다.");
        }

        if (order.status() == OrderStatus.CANCELED) {
            return new CancelOrderResult(orderId, CancelOrderResult.Outcome.ALREADY_CANCELED,
                    "해당 주문은 이미 취소된 상태입니다. (취소 사유: " + order.canceledReason() + ")");
        }

        if (!order.isCancelable()) {
            return new CancelOrderResult(orderId, CancelOrderResult.Outcome.NOT_CANCELABLE,
                    "조리가 이미 시작되어(" + order.status() + ") 자동 취소가 불가합니다. 상담원 연결이 필요합니다.");
        }

        order.cancel(reason, LocalDateTime.now());
        return new CancelOrderResult(orderId, CancelOrderResult.Outcome.CANCELED,
                "주문이 취소되었습니다. 결제 취소는 카드사에 따라 최대 7영업일이 소요될 수 있습니다.");
    }

    // ------- 변환기 (참고용 — 수정할 필요 없음) -------

    private OrderDetailView toDetailView(Order order) {
        var lines = order.items().stream()
                .map(i -> new OrderDetailView.Line(i.menuName(), i.quantity(), i.unitPrice()))
                .toList();
        return new OrderDetailView(
                order.orderId(),
                order.storeName(),
                lines,
                order.totalAmount(),
                order.status().name(),
                order.orderedAt(),
                order.estimatedDeliveryAt()
        );
    }

    private DeliveryStatusView toDeliveryView(Order order) {
        String message = switch (order.status()) {
            case CREATED, ACCEPTED -> "아직 조리가 시작되지 않았습니다.";
            case COOKING -> "현재 조리 중입니다.";
            case DELIVERING -> "라이더가 배달 중입니다.";
            case DELIVERED -> "배달이 완료되었습니다.";
            case CANCELED -> "취소된 주문입니다.";
        };
        return new DeliveryStatusView(
                order.orderId(),
                order.status().name(),
                order.riderLocation(),
                order.estimatedDeliveryAt(),
                message
        );
    }
}
