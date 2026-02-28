package com.pkv.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI pkvOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PKV API")
                        .description("Personal Knowledge Vault API")
                        .version("v0.0.1"));
    }
}
