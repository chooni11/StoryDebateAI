package com.storydebate.backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OllamaService ollamaService;

    public ChatController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
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

        // 4. AI 답변 및 참고 자료 요청
        Map<String, Object> aiResult =
                ollamaService.askWithSources(
                        question,
                        history,
                        workTitle
                );

        String answer = aiResult.get("answer").toString();

        // 5. 최종 응답 구성
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("question", question);
        result.put("workTitle",
                workTitle == null ? "" : workTitle);
        result.put("answer", answer);
        result.put("history", history);
        result.put("sources", aiResult.get("sources"));

        return result;
    }
}
