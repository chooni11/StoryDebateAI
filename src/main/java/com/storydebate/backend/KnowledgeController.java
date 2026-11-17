package com.storydebate.backend;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    // 기존 작품 자료 수정 API
    @PutMapping("/{id}")
    public Map<String, Object> updateKnowledge(
            @PathVariable long id,
            @RequestBody KnowledgeRequest request) {

        if (id <= 0
                || request.workTitle() == null
                || request.workTitle().isBlank()
                || request.content() == null
                || request.content().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 ID와 작품명, 자료 내용이 필요합니다.");
        }

        int updatedRows = knowledgeService.updateKnowledge(
                id,
                request.workTitle(),
                request.sourceTitle(),
                request.section(),
                request.content()
        );

        if (updatedRows == 0) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "수정할 작품 자료를 찾을 수 없습니다.");
        }

        return Map.of(
                "message", "작품 자료와 임베딩이 수정되었습니다.",
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

        // 작품명이 전달되면 해당 작품의 자료만 검색
        var results = knowledgeSearchService.search(
                request.question(),
                5,
                request.workTitle()
        );

        return Map.of(
                "question", request.question(),
                "workTitle", request.workTitle() == null
                        ? ""
                        : request.workTitle(),
                "count", results.size(),
                "results", results
        );
    }

    // 작품 자료 등록 및 수정 요청 DTO
    public record KnowledgeRequest(
            String workTitle,
            String sourceTitle,
            String section,
            String content) {
    }

    // 작품 자료 검색 요청 DTO
    public record SearchRequest(
            String question,
            String workTitle) {
    }
}