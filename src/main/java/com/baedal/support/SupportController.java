package com.baedal.support;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceAdvisor;

    // [1단계] SYSTEM_PROMPT + Structured Output. [4단계] PerformanceLoggingAdvisor 등록.
    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceAdvisor, new SimpleLoggerAdvisor())
                .build()
                .prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}
