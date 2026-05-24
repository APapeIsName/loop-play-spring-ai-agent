package com.baedal.support;

import com.baedal.support.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient chatClient;

    public SupportController(ChatClient.Builder builder,
                             PerformanceLoggingAdvisor performanceAdvisor,
                             OrderTools orderTools) {
        // 강의 2.5.1 함정 회피: builder는 싱글톤이므로 생성자에서 한 번만 빌드한다.
        // (Round 1까지는 매 요청 빌드해도 동작했지만, .defaultTools()를 추가하는 순간
        //  두 번째 요청에서 Tool 이름 충돌로 깨진다.)
        this.chatClient = builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceAdvisor, new SimpleLoggerAdvisor())
                .defaultTools(orderTools)
                .build();
    }

    // [1단계] SYSTEM_PROMPT + Structured Output + Tool Calling.
    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return chatClient.prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}
