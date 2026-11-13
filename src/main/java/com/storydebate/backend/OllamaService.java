package com.storydebate.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private final RestClient restClient;
    private final String model;
    private final KnowledgeSearchService knowledgeSearchService;

    private static final String SYSTEM_PROMPT = """
            너는 StoryDebate AI라는 문학 작품 토론 전문 AI다.
            사용자가 작품을 깊이 이해하고 다양한 관점에서 생각할 수 있도록 돕는다.

            다음 규칙을 반드시 지켜라.

            [1. 답변의 기본 원칙]
            - 자연스럽고 이해하기 쉬운 한국어로 답변한다.
            - 사용자의 질문에 가장 먼저 직접 답변한다.
            - 질문과 관계없는 내용을 불필요하게 반복하지 않는다.
            - 특별히 긴 설명이 필요하지 않다면 적절한 분량으로 답변한다.

            [2. 작품 자료 활용]
            - 제공된 작품 자료를 가장 우선적인 근거로 활용한다.
            - 자료에서 확인할 수 있는 사실과 AI의 해석을 구분한다.
            - 자료에 없는 작품 속 사건, 대사, 인물의 행동을 사실처럼 만들어 내지 않는다.
            - 정확한 대사가 자료에 없다면 임의로 따옴표를 사용해 인용하지 않는다.
            - 자료가 부족하면 어떤 내용이 부족한지 솔직하게 설명한다.
            - 자료가 부족하더라도 일반적으로 알려진 작품 해석을 제시할 경우,
              자료에 근거한 사실과 구분하여 설명한다.

            [3. 대화 맥락 유지]
            - 이전 대화 기록을 참고하여 현재 질문의 의미를 파악한다.
            - '그렇다면', '그 내용', '그 인물'과 같은 표현은
              이전 대화의 맥락을 바탕으로 이해한다.
            - 이전에 설명한 내용을 그대로 반복하지 않는다.
            - 이전 답변과 연결되는 새로운 질문에는 추가적인 설명과 분석을 제공한다.
            - 이전 대화와 현재 질문이 서로 연결되어 있다면
              그 관계를 명확하게 설명한다.

            [4. 문학 작품 토론]
            - 작품 속 인물의 행동, 사건, 가치관과 주제를 중심으로 설명한다.
            - 필요하면 서로 다른 해석이나 반대 관점을 제시한다.
            - 사용자의 주장에 무조건 동의하지 말고 논리와 근거를 검토한다.
            - 정답을 단정하기보다는 사용자가 스스로 생각할 수 있도록 돕는다.

            [5. 답변 구성]
            질문의 성격에 따라 다음 구성을 활용한다.

            1) 핵심 답변
            2) 작품 자료를 바탕으로 한 근거
            3) 추가적인 해석 또는 다른 관점
            4) 필요한 경우 이어지는 토론 질문

            모든 항목을 억지로 포함하지 말고 질문에 필요한 내용만 사용한다.

            [6. 최종 주의사항]
            - 동일한 설명을 여러 번 반복하지 않는다.
            - 작품에 존재하지 않는 설정이나 대사를 만들어 내지 않는다.
            - 근거가 부족한 내용은 확실하지 않다고 밝힌다.
            - 사용자의 현재 질문에 집중한다.
            """;

    public OllamaService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model,
            KnowledgeSearchService knowledgeSearchService) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.model = model;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    // 기존 호출 방식 유지
    public String ask(
            String question,
            List<Map<String, String>> history,
            String workTitle) {

        return askWithSources(question, history, workTitle)
                .get("answer").toString();
    }

    // AI 답변과 참고 자료를 함께 반환
    public Map<String, Object> askWithSources(
            String question,
            List<Map<String, String>> history,
            String workTitle) {

        // 1. 현재 질문과 관련된 작품 자료 검색
        List<Map<String, Object>> searchResults =
                knowledgeSearchService.search(
                        question, 5, workTitle);

        // 2. 프롬프트 구성
        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_PROMPT);

        prompt.append("\n\n[현재 선택된 작품]\n");
        prompt.append(workTitle != null ? workTitle : "선택된 작품 없음");

        // 3. 검색된 작품 자료 추가
        prompt.append("\n\n[검색된 작품 자료]\n");

        if (searchResults.isEmpty()) {

            prompt.append("""
                    관련 작품 자료를 검색하지 못했습니다.
                    검색 자료에 근거한 구체적인 사실을 단정하지 마세요.
                    자료가 부족하다는 점을 사용자에게 설명하세요.
                    """);

        } else {

            for (int i = 0; i < searchResults.size(); i++) {

                Map<String, Object> result = searchResults.get(i);

                prompt.append("\n자료 ")
                        .append(i + 1)
                        .append("\n");

                prompt.append("작품명: ")
                        .append(result.get("workTitle"))
                        .append("\n");

                prompt.append("자료 출처: ")
                        .append(result.get("sourceTitle"))
                        .append("\n");

                prompt.append("구간: ")
                        .append(result.get("section"))
                        .append("\n");

                prompt.append("내용: ")
                        .append(result.get("content"))
                        .append("\n");
            }
        }

        // 4. 이전 대화 기록 추가
        prompt.append("\n[이전 대화 기록]\n");

        if (history == null || history.isEmpty()) {

            prompt.append("이전 대화가 없습니다.\n");

        } else {

            // 지나치게 긴 대화 기록으로 인한 토큰 낭비 방지
            int startIndex = Math.max(0, history.size() - 10);

            for (int i = startIndex; i < history.size(); i++) {

                Map<String, String> turn = history.get(i);

                String role = turn.get("role");
                String content = turn.get("content");

                if (role != null && content != null) {

                    String speaker = role.equals("assistant")
                            ? "StoryDebate AI"
                            : "사용자";

                    prompt.append(speaker)
                            .append(": ")
                            .append(content)
                            .append("\n");
                }
            }
        }

        // 5. 현재 질문 추가
        prompt.append("\n[현재 사용자 질문]\n");
        prompt.append(question);

        prompt.append("""

                [답변 작성 지시]
                1. 현재 질문에 직접 답변하세요.
                2. 관련 작품 자료에서 확인되는 내용을 근거로 활용하세요.
                3. 이전 대화와 연결되는 질문이라면 앞선 내용을 이어서 설명하세요.
                4. 이미 설명한 내용을 불필요하게 반복하지 마세요.
                5. 자료에서 확인할 수 없는 사실이나 대사를 만들어 내지 마세요.
                6. 사실과 해석을 구분하여 자연스러운 한국어로 답변하세요.
                """);

        // 6. Ollama 요청 데이터 구성
        Map<String, Object> options = Map.of(
                "temperature", 0.3,
                "num_predict", 700
        );

        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", prompt.toString(),
                "stream", false,
                "options", options
        );

        // 7. Ollama API 호출
        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("response") == null) {
            throw new IllegalStateException(
                    "Ollama 응답이 비어 있습니다.");
        }

        String answer = response.get("response").toString().trim();

        // 8. 참고 자료 구성
        List<Map<String, Object>> sources = new ArrayList<>();

        for (Map<String, Object> result : searchResults) {

            Map<String, Object> source = new LinkedHashMap<>();

            source.put("id", result.get("id"));
            source.put("workTitle", result.get("workTitle"));
            source.put("sourceTitle", result.get("sourceTitle"));
            source.put("section", result.get("section"));
            source.put("content", result.get("content"));

            sources.add(source);
        }

        // 9. 최종 응답 반환
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("answer", answer);
        result.put("sources", sources);

        return result;
    }
}
