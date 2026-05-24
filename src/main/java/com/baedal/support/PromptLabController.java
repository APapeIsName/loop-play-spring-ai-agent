package com.baedal.support;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prompt-lab")
public class PromptLabController {

    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceAdvisor;

    // [2단계] 프롬프트 정량 비교 실험 엔드포인트.
    // systemPrompt/temperature/repeat 를 받아 repeat 회 호출, 통계 + 원본 응답 반환.
    @PostMapping
    public PromptLabResult experiment(@RequestBody PromptLabRequest req) {
        var client = builder.defaultSystem(req.systemPrompt())
                .defaultAdvisors(performanceAdvisor).build();
        var results = new ArrayList<SupportResponse>();
        for (int i = 0; i < req.repeat(); i++) {
            var spec = client.prompt().user(req.message());
            if (req.temperature() != null) {
                spec = spec.options(OllamaOptions.builder()
                        .temperature(req.temperature()).build());
            }
            results.add(spec.call().entity(SupportResponse.class));
        }
        return PromptLabResult.from(results);
    }

    public record PromptLabRequest(
            String systemPrompt,
            String message,
            int repeat,
            Double temperature   // null 이면 application.yml 기본값(0.3)
    ) {}

    public record PromptLabResult(
            int totalRuns,
            Map<String, Long> categoryCounts,
            Map<String, Long> urgencyCounts,
            double categoryConsistency,
            List<SupportResponse> samples   // 원본 응답(실패 관찰용)
    ) {
        public static PromptLabResult from(List<SupportResponse> results) {
            var catCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.category().name(), Collectors.counting()));
            var urgCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.urgency().name(), Collectors.counting()));
            long maxCat = catCounts.values().stream()
                    .mapToLong(Long::longValue).max().orElse(0);

            return new PromptLabResult(
                    results.size(), catCounts, urgCounts,
                    results.isEmpty() ? 0 : (double) maxCat / results.size(),
                    results
            );
        }
    }
}
