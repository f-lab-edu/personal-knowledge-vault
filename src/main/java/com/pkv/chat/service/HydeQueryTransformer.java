package com.pkv.chat.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Profile("api")
public class HydeQueryTransformer {

    private static final String QUESTION_TOKEN = "{{question}}";

    private final ChatModel chatModel;
    private final String template;

    public HydeQueryTransformer(
            ChatModel chatModel,
            ResourceLoader resourceLoader,
            @Value("${chat.prompt.hyde-path:classpath:prompts/chat/hyde.prompt.md}") String hydePath
    ) {
        this.chatModel = chatModel;
        this.template = loadTemplate(resourceLoader, hydePath);
    }

    public String transform(String query) {
        try {
            String prompt = template.replace(QUESTION_TOKEN, query);
            String hydeDocument = chatModel.chat(prompt);

            if (hydeDocument == null || hydeDocument.isBlank()) {
                log.warn("HyDE 가상 문서 생성 실패 - 빈 응답. 원본 쿼리를 사용합니다.");
                return query;
            }

            log.debug("HyDE 변환 완료. query='{}', hydeDoc='{}'", query, hydeDocument);
            return hydeDocument;
        } catch (Exception e) {
            log.warn("HyDE 변환 실패. 원본 쿼리를 사용합니다. query='{}'", query, e);
            return query;
        }
    }

    private String loadTemplate(ResourceLoader resourceLoader, String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.error("프롬프트 템플릿 로딩 실패 [hyde] - 템플릿 파일이 존재하지 않습니다: {}", path);
                throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED);
            }

            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                log.error("프롬프트 템플릿 로딩 실패 [hyde] - 템플릿 파일이 비어 있습니다: {}", path);
                throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED);
            }

            return content.strip();
        } catch (IOException e) {
            log.error("프롬프트 템플릿 로딩 실패 [hyde] - 템플릿 파일을 읽을 수 없습니다: {}", path, e);
            throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED, e);
        }
    }
}