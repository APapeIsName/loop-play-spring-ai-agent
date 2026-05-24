package com.baedal.support;

import com.baedal.support.domain.OrderMockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2단계 멱등성 실험 전용: 매 trial 시작 시 Mock 시드 상태로 되돌리기 위한 내부 엔드포인트.
 * 실서비스 코드가 아니라 *테스트 환경* 전용. 운영 빌드에서는 제거하거나 프로파일로 가둬야 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/_internal")
public class TestResetController {

    private final OrderMockService orderService;

    @PostMapping("/reset")
    public String reset() {
        orderService.resetForTest();
        return "OK";
    }
}
