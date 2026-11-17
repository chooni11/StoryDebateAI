package com.storydebate.backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final OllamaService ollamaService;
    private final KnowledgeSearchService knowledgeSearchService;

    public ChatController(
            OllamaService ollamaService,
            KnowledgeSearchService knowledgeSearchService) {

        this.ollamaService = ollamaService;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @PostMapping
    public Map<String, Object> chat(
            @RequestBody Map<String, Object> request) {

        // 1. 질문 확인
        Object questionValue = request.get("question");

        if (!(questionValue instanceof String question)
                || question.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "질문을 입력해 주세요.");
        }

        // 2. 작품명 확인
        Object workTitleValue = request.get("workTitle");
        String workTitle = null;

        if (workTitleValue instanceof String title
                && !title.isBlank()) {
            workTitle = title.trim();
        }

        // 3. 이전 대화 기록 처리
        List<Map<String, String>> history = new ArrayList<>();
        Object historyValue = request.get("history");

        if (historyValue instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> turn) {
                    Object role = turn.get("role");
                    Object content = turn.get("content");

                    if (role instanceof String
                            && content instanceof String
                            && (((String) role).equals("user")
                            || ((String) role).equals("assistant"))) {

                        history.add(Map.of(
                                "role", (String) role,
                                "content", (String) content
                        ));
                    }
                }
            }
        }

        // 4. 질문에 다른 작품명이 포함되어 있는지 확인
        if (workTitle != null) {

            String mentionedWork =
                    knowledgeSearchService.findOtherMentionedWorkTitle(
                            question,
                            workTitle
                    );

            // 다른 작품이 발견되면 AI 검색을 실행하지 않고 안내
            if (mentionedWork != null) {

                String answer =
                        "현재 선택된 작품은 '" + workTitle + "'입니다.\n\n"
                                + "질문하신 '" + mentionedWork
                                + "'에 대한 답변을 원하시면 작품을 변경해 주세요.";

                Map<String, Object> result = new LinkedHashMap<>();

                result.put("question", question);
                result.put("workTitle", workTitle);
                result.put("answer", answer);
                result.put("history", history);
                result.put("sources", List.of());
                result.put("workMismatch", true);
                result.put("detectedWorkTitle", mentionedWork);

                return result;
            }
        }

        // 5. AI 답변 및 참고 자료 요청
        Map<String, Object> aiResult =
                ollamaService.askWithSources(
                        question,
                        history,
                        workTitle
                );

        String answer = aiResult.get("answer").toString();

        // 6. 최종 응답 구성
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("question", question);
        result.put("workTitle",
                workTitle == null ? "" : workTitle);
        result.put("answer", answer);
        result.put("history", history);
        result.put("sources", aiResult.get("sources"));
        result.put("workMismatch", false);
        result.put("detectedWorkTitle", null);

        return result;
    }
}