package com.abs.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ABS Ledger (Spring)")
                .version("0.1.0")
                .description("A double-entry ledger core: accounts, transactions, "
                        + "derived balances, and an append-only entry record.")
                .license(new License().name("MIT")));
    }
}
