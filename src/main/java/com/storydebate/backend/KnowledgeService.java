package com.storydebate.backend;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KnowledgeService {

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    public KnowledgeService(
            JdbcTemplate jdbcTemplate,
            @Value("${ollama.base-url:http://localhost:11434}")
            String ollamaBaseUrl) {

        this.jdbcTemplate = jdbcTemplate;

        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    // 작품 자료 등록
    public long saveKnowledge(
            String workTitle,
            String sourceTitle,
            String section,
            String content) {

        // 임베딩 생성
        String vectorText = createEmbedding(content);

        // DB에 저장
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO story_chunks
                    (work_title, source_title, section, content, embedding)
                VALUES (?, ?, ?, ?, CAST(? AS vector))
                RETURNING id
                """,
                Long.class,
                workTitle,
                sourceTitle,
                section,
                content,
                vectorText
        );
    }

    // 기존 작품 자료 수정
    public int updateKnowledge(
            long id,
            String workTitle,
            String sourceTitle,
            String section,
            String content) {

        // 수정된 내용으로 임베딩 재생성
        String vectorText = createEmbedding(content);

        // 기존 DB 데이터 수정
        return jdbcTemplate.update(
                """
                UPDATE story_chunks
                SET work_title = ?,
                    source_title = ?,
                    section = ?,
                    content = ?,
                    embedding = CAST(? AS vector)
                WHERE id = ?
                """,
                workTitle,
                sourceTitle,
                section,
                content,
                vectorText,
                id
        );
    }

    // Ollama를 이용한 임베딩 생성
    private String createEmbedding(String content) {

        Map<?, ?> response = restClient.post()
                .uri("/api/embed")
                .body(Map.of(
                        "model", embeddingModel,
                        "input", content
                ))
                .retrieve()
                .body(Map.class);

        if (response == null
                || !(response.get("embeddings") instanceof List<?> embeddings)
                || embeddings.isEmpty()
                || !(embeddings.get(0) instanceof List<?> vector)) {

            throw new IllegalStateException(
                    "Ollama에서 임베딩을 생성하지 못했습니다.");
        }

        // 임베딩 차원 확인
        if (vector.size() != 768) {

            throw new IllegalStateException(
                    "임베딩 차원이 768이 아닙니다: " + vector.size());
        }

        // pgvector 형식으로 변환
        StringBuilder vectorText = new StringBuilder("[");

        for (int i = 0; i < vector.size(); i++) {

            if (i > 0) {
                vectorText.append(",");
            }

            Object value = vector.get(i);

            if (!(value instanceof Number)) {

                throw new IllegalStateException(
                        "임베딩에 숫자가 아닌 값이 포함되어 있습니다.");
            }

            vectorText.append(
                    ((Number) value).doubleValue()
            );
        }

        vectorText.append("]");

        return vectorText.toString();
    }
}