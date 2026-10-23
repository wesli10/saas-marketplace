package br.com.saasfinder.shared.config;

import br.com.saasfinder.shared.error.ProblemDetailFactory;
import br.com.saasfinder.shared.error.ProblemTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP Basic nas rotas de admin (spec secao 5.2), provisorio ate a spec
 * 006-identidade. Todo o resto e publico.
 */
@Configuration
public class SecurityConfig {

    /**
     * Origens liberadas para chamada de navegador. Vazio por padrao, e vazio e o
     * valor certo em producao: a renderizacao do frontend acontece no servidor
     * (spec 002 seccao 6), entao nenhuma pagina chama esta API pelo navegador.
     *
     * <p>Existe para o `next dev` fora do compose, em que localhost:3000 bate em
     * localhost:8080 e o navegador exige CORS.
     */
    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String origensPermitidas;

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, AuthenticationEntryPoint entrada) throws Exception {
        return http
                // API sem cookie e sem sessao: CSRF nao se aplica, e nada de
                // JSESSIONID sendo criado a cada requisicao.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(fonteDeCors()))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas.requestMatchers("/api/v1/admin/**", "/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(entrada))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(entrada))
                .build();
    }

    /**
     * Sem origem configurada, devolve {@code null}: o Spring trata isso como
     * "nenhuma politica de CORS", e o preflight e recusado. Liberar `*` como
     * padrao seria transformar esquecimento em porta aberta.
     */
    private CorsConfigurationSource fonteDeCors() {
        List<String> origens =
                StringUtils.hasText(origensPermitidas) ? List.of(origensPermitidas.split("\\s*,\\s*")) : List.of();

        if (origens.isEmpty()) {
            return requisicao -> null;
        }

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origens);
        // So leitura: as rotas de escrita sao de curadoria, e curadoria nao
        // acontece por navegador nesta fatia.
        cors.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept-Language", "Content-Type"));
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/v1/**", cors);
        return fonte;
    }

    /**
     * Devolve 401 em problem+json, e nao o HTML padrao do container. Sem o
     * WWW-Authenticate o navegador tambem para de abrir aquela caixa de login,
     * que nao faz sentido numa API consumida por outro programa.
     */
    @Bean
    AuthenticationEntryPoint entradaNaoAutorizada(ProblemDetailFactory problemas, ObjectMapper json) {
        return (requisicao, resposta, excecao) -> {
            ProblemDetail corpo = problemas.montar(
                    ProblemTypes.UNAUTHORIZED,
                    problemas.texto("problema.unauthorized.detalhe"),
                    requisicao.getRequestURI(),
                    List.of());

            resposta.setStatus(ProblemTypes.UNAUTHORIZED.status().value());
            resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(resposta.getOutputStream(), corpo);
        };
    }

    /**
     * Um unico usuario, vindo do ambiente. Sem valor embutido de proposito: a
     * aplicacao nao sobe sem ADMIN_USER e ADMIN_PASSWORD, entao nao existe
     * "senha padrao" para alguem esquecer em producao.
     */
    @Bean
    UserDetailsService curador(
            @Value("${ADMIN_USER:}") String usuario,
            @Value("${ADMIN_PASSWORD:}") String senha,
            PasswordEncoder codificador) {

        if (!StringUtils.hasText(usuario) || !StringUtils.hasText(senha)) {
            throw new IllegalStateException(
                    "ADMIN_USER e ADMIN_PASSWORD sao obrigatorios: as rotas de curadoria nao sobem sem credencial.");
        }

        return new InMemoryUserDetailsManager(User.withUsername(usuario)
                .password(codificador.encode(senha))
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
