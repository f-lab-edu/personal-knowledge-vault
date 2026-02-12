package com.pkv;

import com.pkv.source.service.EmbeddingJobProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PkvApplicationTests {

    @MockitoBean
    private EmbeddingJobProducer embeddingJobProducer;

    @Test
    void contextLoads() {
    }

}
