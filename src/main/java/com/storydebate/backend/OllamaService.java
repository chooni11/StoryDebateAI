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
            너는 StoryDebate AI라는 스토리 토론 전문 AI다.

            다음 규칙을 지켜라.

            1. 작품 속 사건, 인물의 선택, 가치관과 윤리적
               딜레마를 중심으로 토론한다.
            2. 사용자의 주장에 무조건 동의하지 말고
               근거와 논리의 타당성을 검토한다.
            3. 필요하면 반대 관점과 다른 해석을 제시한다.
            4. 작품에서 확인되지 않은 내용을 사실처럼
               만들어 내지 않는다. 불확실하면 밝힌다.
            5. 사용자가 스스로 판단하도록 돕는다.
            6. 자연스러운 한국어로 답변한다.
            7. 이전 대화 기록이 있다면 그 맥락을 이어간다.
            8. 이전 답변을 무조건 반복하지 말고,
               사용자의 새로운 주장에 직접 답한다.
            9. 핵심 의견, 근거, 반대 관점,
               후속 질문을 적절히 포함한다.
            10. 제공된 작품 자료를 우선 근거로 사용한다.
            11. 자료에 없는 내용은 확인되지 않는다고 밝힌다.
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

        // 1. 관련 작품 자료 검색
        List<Map<String, Object>> searchResults =
                knowledgeSearchService.search(
                        question, 5, workTitle);

        // 2. AI 프롬프트 구성
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT);

        prompt.append("\n\n[관련 작품 자료]\n");

        if (searchResults.isEmpty()) {
            prompt.append(
                    "관련 자료를 찾지 못했습니다. "
                            + "작품의 구체적인 사실을 지어내지 마세요.\n");
        } else {
            for (Map<String, Object> result : searchResults) {
                prompt.append("\n작품명: ")
                        .append(result.get("workTitle"));

                prompt.append("\n자료 출처: ")
                        .append(result.get("sourceTitle"));

                prompt.append("\n구간: ")
                        .append(result.get("section"));

                prompt.append("\n자료 내용: ")
                        .append(result.get("content"));

                prompt.append("\n");
            }
        }

        // 3. 이전 대화 기록 반영
        prompt.append("\n[이전 대화 기록]\n");

        if (history == null || history.isEmpty()) {
            prompt.append("이전 대화가 없습니다.\n");
        } else {
            for (Map<String, String> turn : history) {
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

        // 4. 현재 질문 반영
        prompt.append("\n[현재 사용자 질문]\n")
                .append(question)
                .append(
                        "\n\n관련 자료를 근거로 답변하세요. "
                                + "자료와 해석을 구분하고 "
                                + "이전 대화의 맥락도 고려하세요.");

        // 5. Ollama 답변 생성
        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", prompt.toString(),
                "stream", false
        );

        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("response") == null) {
            throw new IllegalStateException(
                    "Ollama 응답이 비어 있습니다.");
        }

        String answer = response.get("response").toString();

        // 6. 응답에 사용할 참고 자료 정보 구성
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

        // 7. 답변과 참고 자료 반환
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("answer", answer);
        result.put("sources", sources);

        return result;
    }
}
