package br.com.saasfinder.reviews.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Diz <b>onde</b> o limite de taxa vale (spec secao 8).
 *
 * <p>So nas duas rotas publicas que escrevem. A leitura nao entra: limitar quem
 * apenas le a ficha de um produto seria estragar o catalogo para resolver um
 * problema que ele nao tem.
 *
 * <p>A lista mora no modulo dono das rotas, e nao junto do limitador em
 * {@code shared}: o limitador e generico, e nao tem por que conhecer review.
 */
@Configuration
public class RotasComLimiteConfig implements WebMvcConfigurer {

    private final LimiteDeEnvioInterceptor limite;

    public RotasComLimiteConfig(LimiteDeEnvioInterceptor limite) {
        this.limite = limite;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(limite).addPathPatterns("/api/v1/products/*/reviews", "/api/v1/reviews/resend");
    }
}
