package br.com.saasfinder.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Faz o OpenAPI descrever o JSON que a aplicacao realmente serve.
 *
 * <p>Sem este bean, o springdoc monta os schemas a partir dos nomes dos
 * componentes dos records e ignora a estrategia de nomes do Jackson: o
 * documento anunciava {@code productCount} e {@code sortOrder} enquanto a API
 * respondia {@code product_count} e {@code sort_order} (desvio D-07 da spec
 * 001). Contrato que mente e pior que contrato ausente - quem gera cliente a
 * partir dele compila e le {@code undefined} em producao.
 *
 * <p>O {@link ModelResolver} construido com o ObjectMapper da aplicacao aplica a
 * mesma estrategia de nomes na geracao do schema.
 */
@Configuration
public class OpenApiSchemaConfig {

    @Bean
    ModelResolver modelResolver(ObjectMapper json) {
        return new ModelResolver(json);
    }
}
