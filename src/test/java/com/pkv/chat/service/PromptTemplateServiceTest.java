package com.pkv.chat.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("대화 컨텍스트 유무에 따라 사용자 템플릿을 선택한다")
    void renderUserPrompt_selectsTemplateByConversationContext() throws Exception {
        Path systemTemplate = tempDir.resolve("system.prompt.md");
        Path withContextTemplate = tempDir.resolve("user_with_context.prompt.md");
        Path withoutContextTemplate = tempDir.resolve("user_without_context.prompt.md");

        Files.writeString(systemTemplate, "SYSTEM");
        Files.writeString(withContextTemplate, "WITH {{conversation_context}} | {{question}} | {{sources}}");
        Files.writeString(withoutContextTemplate, "WITHOUT {{question}} | {{sources}}");

        PromptTemplateService service = new PromptTemplateService(
                new DefaultResourceLoader(),
                systemTemplate.toUri().toString(),
                withContextTemplate.toUri().toString(),
                withoutContextTemplate.toUri().toString()
        );

        String withContext = service.renderUserPrompt("질문", "출처", "이전 대화");
        String withoutContext = service.renderUserPrompt("질문", "출처", "");

        assertThat(withContext).isEqualTo("WITH 이전 대화 | 질문 | 출처");
        assertThat(withoutContext).isEqualTo("WITHOUT 질문 | 출처");
    }

    @Test
    @DisplayName("템플릿 파일이 없으면 PkvException(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED)을 던진다")
    void constructor_throwsPkvExceptionWhenTemplateMissing() {
        Path missingTemplate = tempDir.resolve("missing.prompt.md");

        assertThatThrownBy(() -> new PromptTemplateService(
                new DefaultResourceLoader(),
                missingTemplate.toUri().toString(),
                missingTemplate.toUri().toString(),
                missingTemplate.toUri().toString()
        ))
                .isInstanceOf(PkvException.class)
                .satisfies(e -> assertThat(((PkvException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_PROMPT_TEMPLATE_LOAD_FAILED));
    }
}
