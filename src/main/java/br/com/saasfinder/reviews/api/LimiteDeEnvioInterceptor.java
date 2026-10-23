package br.com.saasfinder.reviews.api;

import br.com.saasfinder.shared.error.TooManyRequestsException;
import br.com.saasfinder.shared.web.IpDoCliente;
import br.com.saasfinder.shared.web.LimitadorDeTaxa;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica o limite de taxa nas duas rotas publicas de escrita (spec secao 8).
 *
 * <p>Interceptor, e nao uma chamada dentro do controller: o limite e uma decisao
 * sobre a requisicao, tomada antes de ela virar caso de uso - e assim o controller
 * continua sem regra que nao seja a dele (constituicao secao 6).
 *
 * <p>A excecao sobe para o {@code GlobalExceptionHandler}, que devolve 429 em
 * problem+json com o {@code Retry-After}. Escrever a resposta aqui a mao criaria
 * um segundo formato de erro na mesma API.
 */
@Component
public class LimiteDeEnvioInterceptor implements HandlerInterceptor {

    private final LimitadorDeTaxa limitador;
    private final IpDoCliente ip;

    public LimiteDeEnvioInterceptor(LimitadorDeTaxa limitador, IpDoCliente ip) {
        this.limitador = limitador;
        this.ip = ip;
    }

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler) {
        if (!HttpMethod.POST.matches(requisicao.getMethod())) {
            return true;
        }

        limitador.registrar(ip.de(requisicao)).ifPresent(espera -> {
            throw new TooManyRequestsException(espera);
        });

        return true;
    }
}
