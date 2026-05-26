package com.baedal.support.domain;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 교육용 Mock 주문 저장소.
 * <p>
 * H2/JPA를 쓰지 않는 이유: 2주차 목표는 "Tool Calling 흐름의 이해"이며,
 * DB 세팅이 수강생의 주의를 분산시킨다. 메모리 Map 하나로 충분하다.
 * <p>
 * 실제 서비스에서는 이 클래스가 OrderRepository를 주입받는 OrderService가 될 것이다.
 */
@Slf4j
@Service
public class OrderMockService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        LocalDateTime now = LocalDateTime.now();

        // 2024-1234: 배달 중 — getDeliveryStatus 호출 시 라이더 위치 확인용
        save(new Order(
                "2024-1234",
                "교촌치킨 강남점",
                List.of(
                        new OrderItem("허니콤보", 1, 23_000),
                        new OrderItem("콜라 1.25L", 1, 3_000)
                ),
                now.minusMinutes(20),
                now.plusMinutes(15),
                "서울시 강남구 테헤란로 142",
                "배달 시작 · 현재 역삼역 사거리 부근",
                OrderStatus.DELIVERING));

        // 2024-1235: 주문 직후(CREATED) — cancelOrder → CANCELED 경로용
        save(new Order(
                "2024-1235",
                "버거킹 선릉점",
                List.of(new OrderItem("와퍼 세트", 2, 9_500)),
                now.minusMinutes(5),
                now.plusMinutes(35),
                "서울시 강남구 선릉로 89",
                null,
                OrderStatus.CREATED));

        // 2024-1236: 배달 완료(DELIVERED) — 완료된 주문 상태/상세 조회 시나리오용
        save(new Order(
                "2024-1236",
                "스시미루 역삼점",
                List.of(
                        new OrderItem("연어 초밥 세트", 1, 28_000),
                        new OrderItem("미소된장국", 1, 3_000)
                ),
                now.minusHours(1),
                now.minusMinutes(10),
                "서울시 강남구 역삼로 234",
                null,
                OrderStatus.DELIVERED));

        // 2024-1237: 조리 중(COOKING) — cancelOrder → NOT_CANCELABLE 경로
        save(new Order(
                "2024-1237",
                "맘스터치 강남역점",
                List.of(
                        new OrderItem("싸이버거 세트", 1, 9_800),
                        new OrderItem("치즈스틱", 2, 2_500)
                ),
                now.minusMinutes(10),
                now.plusMinutes(30),
                "서울시 강남구 강남대로 396",
                null,
                OrderStatus.COOKING));

        // 2024-1238: 사전 취소(CANCELED) — cancelOrder → ALREADY_CANCELED 경로 (멱등성)
        // 중요: Order 생성 후 cancel()을 호출해야 canceledReason/canceledAt이 채워진다.
        Order o1238 = new Order(
                "2024-1238",
                "BBQ 선릉역점",
                List.of(new OrderItem("황금올리브치킨", 1, 22_000)),
                now.minusMinutes(30),
                now.plusMinutes(20),
                "서울시 강남구 선릉로 421",
                null,
                OrderStatus.ACCEPTED);
        o1238.cancel("고객 요청 — 주소 잘못 입력", now.minusMinutes(8));
        save(o1238);

        // 2024-1239: 사장님 수락 직후(ACCEPTED) — cancelOrder → CANCELED 경로
        save(new Order(
                "2024-1239",
                "교촌치킨 삼성점",
                List.of(
                        new OrderItem("레드콤보", 1, 24_000),
                        new OrderItem("사이다 1.25L", 1, 3_000)
                ),
                now.minusMinutes(5),
                now.plusMinutes(40),
                "서울시 강남구 삼성로 567",
                null,
                OrderStatus.ACCEPTED));

        log.info("OrderMockService seeded — {}건", orders.size());
    }

    private void save(Order order) {
        orders.put(order.orderId(), order);
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    // 2단계 멱등성 실험용: 매 trial 시작 시 시드 상태로 되돌린다.
    public void resetForTest() {
        orders.clear();
        seed();
    }
}
