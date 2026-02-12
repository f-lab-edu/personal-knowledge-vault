package com.pkv.support;

import com.pkv.source.service.EmbeddingJobProducer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @MockitoBean
    protected EmbeddingJobProducer embeddingJobProducer;

    @MockitoBean
    protected EmbeddingStore<TextSegment> embeddingStore;
}
