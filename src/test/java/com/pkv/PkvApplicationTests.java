package com.pkv;

import com.pkv.source.service.EmbeddingJobProducer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PkvApplicationTests {

    @MockitoBean
    private EmbeddingJobProducer embeddingJobProducer;

    @MockitoBean
    private EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void contextLoads() {
    }

}
