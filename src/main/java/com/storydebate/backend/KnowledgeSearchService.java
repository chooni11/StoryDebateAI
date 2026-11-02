package com.storydebate.backend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KnowledgeSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final String embeddingModel;

    public KnowledgeSearchService(
            JdbcTemplate jdbcTemplate,
            @Value("${ollama.base-url:http://localhost:11434}")
            String ollamaBaseUrl,
            @Value("${ollama.embedding-model:nomic-embed-text}")
            String embeddingModel) {

        this.jdbcTemplate = jdbcTemplate;
        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
        this.embeddingModel = embeddingModel;
    }

    public List<Map<String, Object>> search(
            String question, int limit) {

        return search(question, limit, null);
    }

    public List<Map<String, Object>> search(
            String question, int limit, String workTitle) {

        // 1. 질문을 임베딩 벡터로 변환
        Map<?, ?> response = restClient.post()
                .uri("/api/embed")
                .body(Map.of(
                        "model", embeddingModel,
                        "input", question
                ))
                .retrieve()
                .body(Map.class);

        if (response == null
                || !(response.get("embeddings") instanceof List<?> embeddings)
                || embeddings.isEmpty()
                || !(embeddings.get(0) instanceof List<?> vector)
                || vector.size() != 768) {
            throw new IllegalStateException(
                    "질문 임베딩 생성에 실패했습니다.");
        }

        // 2. 벡터를 pgvector 형식으로 변환
        StringBuilder vectorText = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                vectorText.append(",");
            }

            Object value = vector.get(i);
            if (!(value instanceof Number)) {
                throw new IllegalStateException(
                        "임베딩에 숫자가 아닌 값이 있습니다.");
            }

            vectorText.append(((Number) value).doubleValue());
        }
        vectorText.append("]");

        // 3. 작품명이 지정되면 해당 작품의 자료만 검색
        String sql;
        Object[] params;

        if (workTitle != null && !workTitle.isBlank()) {
            sql = """
                    SELECT
                        id,
                        work_title,
                        source_title,
                        section,
                        content,
                        embedding <=> CAST(? AS vector) AS distance
                    FROM story_chunks
                    WHERE embedding IS NOT NULL
                      AND work_title = ?
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """;

            params = new Object[] {
                    vectorText.toString(),
                    workTitle.trim(),
                    vectorText.toString(),
                    Math.max(1, Math.min(limit, 10))
            };
        } else {
            sql = """
                    SELECT
                        id,
                        work_title,
                        source_title,
                        section,
                        content,
                        embedding <=> CAST(? AS vector) AS distance
                    FROM story_chunks
                    WHERE embedding IS NOT NULL
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """;

            params = new Object[] {
                    vectorText.toString(),
                    vectorText.toString(),
                    Math.max(1, Math.min(limit, 10))
            };
        }

        // 4. 검색 결과 반환
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("id", rs.getLong("id"));
                    result.put("workTitle",
                            rs.getString("work_title"));
                    result.put("sourceTitle",
                            rs.getString("source_title"));
                    result.put("section",
                            rs.getString("section"));
                    result.put("content",
                            rs.getString("content"));
                    result.put("distance",
                            rs.getDouble("distance"));

                    return result;
                },
                params
        );
    }
}
