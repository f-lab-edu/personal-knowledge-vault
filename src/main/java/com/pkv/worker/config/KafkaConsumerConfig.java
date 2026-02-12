package com.pkv.worker.config;

import com.pkv.worker.consumer.EmbeddingPipelineConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@Profile("worker")
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final EmbeddingPipelineConsumer embeddingPipelineConsumer;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> embeddingKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);

        ExponentialBackOff exponentialBackOff = new ExponentialBackOff(1000L, 2.0);
        exponentialBackOff.setMaxAttempts(2);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                embeddingPipelineConsumer::recoverFailedEmbedding,
                exponentialBackOff
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
