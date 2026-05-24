package com.baedal.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder,
                          PerformanceLoggingAdvisor performanceAdvisor) {
        // 4단계 토큰 비교를 위해 PerformanceLoggingAdvisor 등록.
        // (다른 컨트롤러들과 일관성 + Tool 없는 baseline 토큰 측정용)
        this.chatClient = builder
                .defaultAdvisors(performanceAdvisor)
                .build();
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt()
                .user(request.message())
                .call()
                .content();
    }
}
