package com.pkv.chat.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Profile("api")
@Transactional(readOnly = true)
public class PromptTemplateService {

    private static final String QUESTION_TOKEN = "{{question}}";
    private static final String SOURCES_TOKEN = "{{sources}}";
    private static final String CONTEXT_TOKEN = "{{conversation_context}}";

    private final String systemTemplate;
    private final String withContextTemplate;
    private final String withoutContextTemplate;

    public PromptTemplateService(
            ResourceLoader resourceLoader,
            @Value("${chat.prompt.system-path:classpath:prompts/chat/system.prompt.md}") String systemPromptPath,
            @Value("${chat.prompt.user-with-context-path:classpath:prompts/chat/user_with_context.prompt.md}") String withContextPath,
            @Value("${chat.prompt.user-without-context-path:classpath:prompts/chat/user_without_context.prompt.md}") String withoutContextPath
    ) {
        this.systemTemplate = loadTemplate(resourceLoader, systemPromptPath, "system");
        this.withContextTemplate = loadTemplate(resourceLoader, withContextPath, "user_with_context");
        this.withoutContextTemplate = loadTemplate(resourceLoader, withoutContextPath, "user_without_context");
    }

    public String systemPrompt() {
        return systemTemplate;
    }

    public String renderUserPrompt(String question, String sourceBlock, String conversationContextBlock) {
        String template = StringUtils.hasText(conversationContextBlock)
                ? withContextTemplate
                : withoutContextTemplate;

        return template
                .replace(CONTEXT_TOKEN, nullToEmpty(conversationContextBlock))
                .replace(QUESTION_TOKEN, nullToEmpty(question))
                .replace(SOURCES_TOKEN, nullToEmpty(sourceBlock));
    }

    private String loadTemplate(
            ResourceLoader resourceLoader,
            String path,
            String templateName
    ) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.error("프롬프트 템플릿 로딩 실패 [{}] - 템플릿 파일이 존재하지 않습니다: {}", templateName, path);
                throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED);
            }

            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                log.error("프롬프트 템플릿 로딩 실패 [{}] - 템플릿 파일이 비어 있습니다: {}", templateName, path);
                throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED);
            }

            return content.strip();
        } catch (IOException e) {
            log.error("프롬프트 템플릿 로딩 실패 [{}] - 템플릿 파일을 읽을 수 없습니다: {}", templateName, path, e);
            throw new PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED, e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
