package com.storydebate.backend;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeController(
            KnowledgeService knowledgeService,
            KnowledgeSearchService knowledgeSearchService) {

        this.knowledgeService = knowledgeService;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    // 작품 자료 등록 API
    @PostMapping
    public Map<String, Object> addKnowledge(
            @RequestBody KnowledgeRequest request) {

        if (request.workTitle() == null
                || request.workTitle().isBlank()
                || request.content() == null
                || request.content().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "작품명과 자료 내용은 필수입니다.");
        }

        long id = knowledgeService.saveKnowledge(
                request.workTitle(),
                request.sourceTitle(),
                request.section(),
                request.content()
        );

        return Map.of(
                "message", "작품 자료가 저장되었습니다.",
                "id", id,
                "workTitle", request.workTitle()
        );
    }

    // 작품 자료 검색 API
    @PostMapping("/search")
    public Map<String, Object> searchKnowledge(
            @RequestBody SearchRequest request) {

        if (request.question() == null
                || request.question().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색 질문은 필수입니다.");
        }

        var results = knowledgeSearchService.search(
                request.question(),
                5
        );

        return Map.of(
                "question", request.question(),
                "count", results.size(),
                "results", results
        );
    }

    // 작품 자료 등록 요청 DTO
    public record KnowledgeRequest(
            String workTitle,
            String sourceTitle,
            String section,
            String content) {
    }

    // 작품 자료 검색 요청 DTO
    public record SearchRequest(String question) {
    }
}
