package br.com.saasfinder.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documento OpenAPI da API (AC-23).
 *
 * <p>O esquema de seguranca e declarado aqui, uma vez, e referenciado pelas rotas
 * de curadoria com {@code @SecurityRequirement}: assim o "Authorize" da Swagger
 * UI funciona e fica evidente quais rotas exigem credencial.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema, usado nas anotacoes dos controllers de admin. */
    public static final String BASIC = "curadoria";

    @Bean
    OpenAPI documentoDaApi(@Value("${server.port:8080}") int porta) {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketplace bilingue de SaaS - catalogo")
                        .version("v1")
                        .description(
                                """
                                Catalogo bilingue (pt-BR / en) de solucoes SaaS: arvore de categorias,
                                busca full-text com fallback por similaridade, ficha de produto e
                                comparacao, mais as rotas de curadoria protegidas por HTTP Basic.

                                O idioma da resposta vem do header `Accept-Language` ou do parametro
                                `lang`, e o parametro vence. Valores aceitos: `pt-BR` e `en`.

                                Erros seguem a RFC 9457 (`application/problem+json`).
                                """)
                        .contact(new Contact().name("Curadoria SaaS Finder"))
                        .license(new License().name("Uso interno")))
                .servers(List.of(
                        new Server().url("http://localhost:" + porta).description("Ambiente local"),
                        new Server().url("/").description("Mesma origem de quem serve a UI")))
                .components(new Components()
                        .addSecuritySchemes(
                                BASIC,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("Credencial de curadoria (ADMIN_USER / ADMIN_PASSWORD). "
                                                + "Exigida em todo /api/v1/admin.")));
    }
}
