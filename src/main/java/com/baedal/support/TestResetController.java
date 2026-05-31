package com.baedal.support;

import com.baedal.support.domain.OrderMockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 측정 인프라 전용: 매 trial 시작 시 Mock 시드 상태로 되돌리고 ETA를 현재 시각 기준으로 재생성.
 * Round-2에서 만든 패턴(/api/v1/_internal/reset) 복원. round-3 raw 측정에서도 동일하게 사용.
 * 실서비스 빌드에서는 제거하거나 프로파일로 가둬야 함.
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
