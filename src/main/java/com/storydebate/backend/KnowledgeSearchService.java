package com.storydebate.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KnowledgeSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final String embeddingModel;

    // 등장인물별 지식 자료의 제목
    private static final Map<String, String> CHARACTER_SOURCE_TITLES = Map.of(
            "왕", "왕의 권력과 지배욕 - 여행 중 만난 어른들 - 왕",
            "사업가", "사업가의 소유욕과 숫자 중심의 사고",
            "허영쟁이", "허영쟁이의 인정 욕구와 자기중심성 - 여행 중 만난 어른들 - 허영쟁이",
            "술꾼", "술꾼의 현실 도피와 악순환 - 여행 중 만난 어른들 - 술꾼",
            "가로등지기", "가로등지기의 성실함과 의무",
            "지리학자", "지리학자의 지식과 경험의 간극 - 여행 중 만난 어른들 - 지리학자",
            "여우", "여우와 어린 왕자의 만남 - 여우의 역할과 관계의 의미",
            "장미", "장미와 어린 왕자의 관계 - 인물 관계와 사랑의 의미",
            "바오밥나무", "바오밥나무의 상징과 의미 - 상징과 작품의 교훈"
    );

    // 작품별 등장인물 및 별칭
    private static final Map<String, List<String>> WORK_CHARACTER_ALIASES = Map.of(
            "흥부와 놀부", List.of(
                    "흥부",
                    "놀부",
                    "제비"
            ),
            "어린 왕자", List.of(
                    "어린왕자",
                    "왕",
                    "사업가",
                    "허영쟁이",
                    "술꾼",
                    "가로등지기",
                    "지리학자",
                    "여우",
                    "장미",
                    "장미꽃",
                    "바오밥나무"
            )
    );

    // 등장인물별 검색 키워드
    private static final Map<String, List<String>> CHARACTER_KEYWORD_ALIASES = Map.of(
            "왕", List.of("왕"),
            "사업가", List.of("사업가"),
            "허영쟁이", List.of("허영쟁이"),
            "술꾼", List.of("술꾼"),
            "가로등지기", List.of("가로등지기"),
            "지리학자", List.of("지리학자"),
            "여우", List.of("여우"),
            "장미", List.of("장미", "장미꽃"),
            "바오밥나무", List.of("바오밥나무")
    );

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

    // 현재 선택된 작품이 아닌 다른 작품의 제목 또는 등장인물 확인
    public String findOtherMentionedWorkTitle(
            String question,
            String selectedWorkTitle) {

        if (question == null || question.isBlank()) {
            return null;
        }

        String sql = """
                SELECT work_title
                FROM story_chunks
                WHERE work_title IS NOT NULL
                  AND TRIM(work_title) <> ''
                GROUP BY work_title
                ORDER BY LENGTH(work_title) DESC
                """;

        List<String> workTitles =
                jdbcTemplate.queryForList(sql, String.class);

        String normalizedQuestion = normalizeTitle(question);

        String normalizedSelected =
                normalizeTitle(selectedWorkTitle);

        for (String title : workTitles) {

            if (title == null || title.isBlank()) {
                continue;
            }

            String trimmedTitle = title.trim();
            String normalizedTitle = normalizeTitle(trimmedTitle);

            // 현재 선택된 작품은 검사하지 않음
            if (normalizedTitle.equals(normalizedSelected)) {
                continue;
            }

            // 작품 제목 자체가 질문에 포함되어 있는지 확인
            if (normalizedQuestion.contains(normalizedTitle)) {
                return trimmedTitle;
            }

            // 해당 작품의 등장인물 별칭 확인
            List<String> aliases =
                    WORK_CHARACTER_ALIASES.get(trimmedTitle);

            if (aliases == null) {
                continue;
            }

            for (String alias : aliases) {

                if (containsAlias(question, alias)) {
                    return trimmedTitle;
                }
            }
        }

        return null;
    }

    // 등장인물 별칭이 질문에 포함되어 있는지 확인
    private boolean containsAlias(String question, String alias) {

        if (question == null || alias == null) {
            return false;
        }

        // '왕'은 '어린 왕자'의 일부로 잘못 인식하지 않도록 별도 검사
        if (alias.equals("왕")) {
            return containsStandaloneKing(question);
        }

        return normalizeTitle(question)
                .contains(normalizeTitle(alias));
    }

    // 띄어쓰기 무시 및 소문자 변환
    private String normalizeTitle(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    // 질문에서 등장인물 관련 검색 키워드 추출
    private List<String> extractKeywords(String question) {

        if (question == null || question.isBlank()) {
            return List.of();
        }

        Set<String> keywords = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry
                : CHARACTER_KEYWORD_ALIASES.entrySet()) {

            String keyword = entry.getKey();

            // '왕'이 '어린 왕자'의 일부로 잘못 인식되는 것을 방지
            if (keyword.equals("왕")) {

                if (containsStandaloneKing(question)) {
                    keywords.add(keyword);
                }

                continue;
            }

            for (String alias : entry.getValue()) {

                if (containsAlias(question, alias)) {
                    keywords.add(keyword);
                    break;
                }
            }
        }

        return new ArrayList<>(keywords);
    }

    // '왕은', '왕의', '왕과' 등 독립적인 왕 표현 확인
    private boolean containsStandaloneKing(String question) {

        return java.util.regex.Pattern.compile(
                "(?<![가-힣])왕"
                        + "(은|의|이란|이라는|이|과|도|에게서|에게|처럼|에|으로|에서)?"
                        + "(?![가-힣])"
        ).matcher(question).find();
    }

    // 질문 임베딩 생성
    private String createQuestionVector(String question) {

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
                    "질문 임베딩 생성에 실패했습니다."
            );
        }

        StringBuilder vectorText = new StringBuilder("[");

        for (int i = 0; i < vector.size(); i++) {

            if (i > 0) {
                vectorText.append(",");
            }

            Object value = vector.get(i);

            if (!(value instanceof Number)) {
                throw new IllegalStateException(
                        "임베딩에 숫자가 아닌 값이 있습니다."
                );
            }

            vectorText.append(
                    ((Number) value).doubleValue()
            );
        }

        vectorText.append("]");

        return vectorText.toString();
    }

    // 기존 벡터 검색 호출 방식 유지
    public List<Map<String, Object>> search(
            String question,
            int limit) {

        return search(question, limit, null);
    }

    // 통합 검색
    public List<Map<String, Object>> search(
            String question,
            int limit,
            String workTitle) {

        if (question == null || question.isBlank()) {
            return List.of();
        }

        // 검색 개수 제한: 최소 1개, 최대 10개
        int searchLimit = Math.max(1, Math.min(limit, 10));

        // 질문에서 등장인물 추출
        List<String> keywords = extractKeywords(question);

        // 등장인물이 명시된 경우 해당 인물의 자료만 검색
        if (!keywords.isEmpty()) {

            List<Map<String, Object>> characterResults =
                    searchByCharacterKeywords(
                            keywords,
                            question,
                            workTitle,
                            searchLimit
                    );

            // 일치하는 자료가 없어도 일반 벡터 검색으로 넘어가지 않음
            return characterResults;
        }

        // 등장인물이 명시되지 않은 일반 질문은 기존 벡터 검색 사용
        String vectorText = createQuestionVector(question);

        return searchByVector(
                vectorText,
                workTitle,
                searchLimit
        );
    }

    // 등장인물에 해당하는 자료만 정확하게 검색
    private List<Map<String, Object>> searchByCharacterKeywords(
            List<String> keywords,
            String question,
            String workTitle,
            int limit) {

        // 검색 대상 자료 제목 수집
        Set<String> sourceTitleSet = new LinkedHashSet<>();

        for (String keyword : keywords) {

            String sourceTitle =
                    CHARACTER_SOURCE_TITLES.get(keyword);

            if (sourceTitle != null) {
                sourceTitleSet.add(sourceTitle);
            }
        }

        List<String> sourceTitles =
                new ArrayList<>(sourceTitleSet);

        if (sourceTitles.isEmpty()) {
            return List.of();
        }

        // 해당 인물 자료만 검색
        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    work_title,
                    source_title,
                    section,
                    content,
                    embedding <=> CAST(? AS vector) AS distance
                FROM story_chunks
                WHERE embedding IS NOT NULL
                  AND source_title IN (
                """);

        List<Object> params = new ArrayList<>();

        // SELECT 절의 질문 벡터
        // 인물 검색에서도 유사도 순서를 유지하기 위해 사용
        String vectorText = createQuestionVector(question);

        params.add(vectorText);

        for (int i = 0; i < sourceTitles.size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("?");

            params.add(sourceTitles.get(i));
        }

        sql.append(")\n");

        // 작품 선택 조건
        if (workTitle != null && !workTitle.isBlank()) {

            sql.append(" AND work_title = ?\n");

            params.add(workTitle.trim());
        }

        sql.append("""
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """);

        params.add(vectorText);
        params.add(limit);

        return executeSearch(
                sql.toString(),
                params.toArray()
        );
    }

    // 벡터 유사도 기반 검색
    private List<Map<String, Object>> searchByVector(
            String vectorText,
            String workTitle,
            int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    work_title,
                    source_title,
                    section,
                    content,
                    embedding <=> CAST(? AS vector) AS distance
                FROM story_chunks
                WHERE embedding IS NOT NULL
                """);

        List<Object> params = new ArrayList<>();

        // SELECT 절의 질문 벡터
        params.add(vectorText);

        // 작품 선택 조건
        if (workTitle != null && !workTitle.isBlank()) {

            sql.append(" AND work_title = ?\n");

            params.add(workTitle.trim());
        }

        sql.append("""
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """);

        params.add(vectorText);
        params.add(limit);

        return executeSearch(
                sql.toString(),
                params.toArray()
        );
    }

    // 검색 결과 공통 처리
    private List<Map<String, Object>> executeSearch(
            String sql,
            Object... params) {

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {

                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("id", rs.getLong("id"));

                    result.put(
                            "workTitle",
                            rs.getString("work_title")
                    );

                    result.put(
                            "sourceTitle",
                            rs.getString("source_title")
                    );

                    result.put(
                            "section",
                            rs.getString("section")
                    );

                    result.put(
                            "content",
                            rs.getString("content")
                    );

                    result.put(
                            "distance",
                            rs.getDouble("distance")
                    );

                    return result;
                },
                params
        );
    }
}
