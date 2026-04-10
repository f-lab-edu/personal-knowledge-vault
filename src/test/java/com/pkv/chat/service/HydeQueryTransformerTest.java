package com.pkv.chat.service;

import com.pkv.chat.dto.HydeResult;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HydeQueryTransformerTest {

    private static final String QUERY = "팩토리 패턴이 뭐야?";
    private static final String DUMMY_TEMPLATE = "질문: {{question}}";

    @Mock
    private ChatModel chatModel;

    @Mock
    private ResourceLoader resourceLoader;

    private HydeQueryTransformer hydeQueryTransformer;

    @BeforeEach
    void setUp() {
        ByteArrayResource resource = new ByteArrayResource(DUMMY_TEMPLATE.getBytes(StandardCharsets.UTF_8));
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        hydeQueryTransformer = new HydeQueryTransformer(chatModel, resourceLoader, "classpath:dummy");
    }

    @Test
    @DisplayName("정상 응답 - 한국어/영어 가상 문서를 각각 파싱한다")
    void parseResponseWithBothDelimiters() {
        String response = """
                ---KO---
                팩토리 패턴은 객체 생성을 캡슐화하는 디자인 패턴이다.
                ---EN---
                The Factory Pattern is a design pattern that encapsulates object creation.""";

        HydeResult result = hydeQueryTransformer.parseResponse(response, QUERY);

        assertThat(result.ko()).isEqualTo("팩토리 패턴은 객체 생성을 캡슐화하는 디자인 패턴이다.");
        assertThat(result.en()).isEqualTo("The Factory Pattern is a design pattern that encapsulates object creation.");
        assertThat(result.documents()).hasSize(2);
    }

    @Test
    @DisplayName("구분자 누락 시 전체 응답을 한국어로, 원본 쿼리를 영어로 사용한다")
    void parseResponseWithoutDelimitersFallsBack() {
        String response = "팩토리 패턴은 객체 생성을 캡슐화하는 디자인 패턴이다.";

        HydeResult result = hydeQueryTransformer.parseResponse(response, QUERY);

        assertThat(result.ko()).isEqualTo(response);
        assertThat(result.en()).isEqualTo(QUERY);
    }

    @Test
    @DisplayName("한국어 섹션이 비어있으면 원본 쿼리로 대체한다")
    void parseResponseWithEmptyKoSection() {
        String response = """
                ---KO---

                ---EN---
                The Factory Pattern encapsulates object creation.""";

        HydeResult result = hydeQueryTransformer.parseResponse(response, QUERY);

        assertThat(result.ko()).isEqualTo(QUERY);
        assertThat(result.en()).isEqualTo("The Factory Pattern encapsulates object creation.");
    }

    @Test
    @DisplayName("영어 섹션이 비어있으면 원본 쿼리로 대체한다")
    void parseResponseWithEmptyEnSection() {
        String response = """
                ---KO---
                팩토리 패턴은 객체 생성을 캡슐화하는 디자인 패턴이다.
                ---EN---
                """;

        HydeResult result = hydeQueryTransformer.parseResponse(response, QUERY);

        assertThat(result.ko()).isEqualTo("팩토리 패턴은 객체 생성을 캡슐화하는 디자인 패턴이다.");
        assertThat(result.en()).isEqualTo(QUERY);
    }

    @Test
    @DisplayName("LLM 응답이 null이면 양쪽 모두 원본 쿼리를 사용한다")
    void transformWithNullResponse() {
        given(chatModel.chat(anyString())).willReturn(null);

        HydeResult result = hydeQueryTransformer.transform(QUERY);

        assertThat(result.ko()).isEqualTo(QUERY);
        assertThat(result.en()).isEqualTo(QUERY);
    }

    @Test
    @DisplayName("LLM 응답이 빈 문자열이면 양쪽 모두 원본 쿼리를 사용한다")
    void transformWithBlankResponse() {
        given(chatModel.chat(anyString())).willReturn("   ");

        HydeResult result = hydeQueryTransformer.transform(QUERY);

        assertThat(result.ko()).isEqualTo(QUERY);
        assertThat(result.en()).isEqualTo(QUERY);
    }

    @Test
    @DisplayName("LLM 호출 예외 시 양쪽 모두 원본 쿼리를 사용한다")
    void transformWithException() {
        given(chatModel.chat(anyString())).willThrow(new RuntimeException("LLM error"));

        HydeResult result = hydeQueryTransformer.transform(QUERY);

        assertThat(result.ko()).isEqualTo(QUERY);
        assertThat(result.en()).isEqualTo(QUERY);
    }
}
