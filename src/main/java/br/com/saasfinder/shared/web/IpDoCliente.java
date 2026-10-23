package br.com.saasfinder.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * De qual endereco veio a requisicao (plano DT-09).
 *
 * <p>{@code X-Forwarded-For} so e lido quando alguem <b>configurou</b> que ha um
 * proxy na frente. O default e o IP da conexao, e o motivo e simples: sem proxy,
 * o header e escrito por quem quiser, e qualquer limite por IP vira decoracao -
 * basta mandar um endereco diferente a cada chamada.
 *
 * <p>Ligar isto sem proxy de verdade na frente e pior do que nao ter limite
 * nenhum, porque da a impressao de que ha um.
 */
@Component
public class IpDoCliente {

    private final boolean confiarNoProxy;

    public IpDoCliente(@Value("${app.rate-limit.confiar-em-forwarded-for}") boolean confiarNoProxy) {
        this.confiarNoProxy = confiarNoProxy;
    }

    public String de(HttpServletRequest requisicao) {
        if (confiarNoProxy) {
            String encaminhado = requisicao.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(encaminhado)) {
                // O primeiro da lista e o cliente original; o resto sao os proxies.
                return encaminhado.split(",")[0].trim();
            }
        }
        return requisicao.getRemoteAddr();
    }
}
