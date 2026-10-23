package br.com.saasfinder.shared.locale;

import br.com.saasfinder.shared.error.ProblemDetailFactory;
import br.com.saasfinder.shared.error.ProblemTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolve o idioma da requisicao (spec secao 5):
 *
 * <ol>
 *   <li>{@code ?lang} explicito, que vence o header (AC-08). Valor fora de pt-BR
 *       e en resulta em 400 (AC-09).
 *   <li>{@code Accept-Language}, respeitando os pesos q. Header so com idioma nao
 *       suportado cai no padrao, sem erro - e o que a negociacao de conteudo HTTP
 *       manda fazer, e um navegador em frances nao deve tomar 400 numa API
 *       publica (desvio D-03).
 *   <li>{@code APP_DEFAULT_LOCALE}, que por padrao e pt-BR.
 * </ol>
 *
 * <p>E um filtro, e nao um interceptor, porque tambem precisa valer para
 * requisicoes que nunca chegam a um controller - o Content-Language sai em toda
 * resposta, inclusive nos 404. Como filtro roda fora do DispatcherServlet, uma
 * excecao aqui nao passaria pelo @RestControllerAdvice; por isso o 400 e escrito
 * direto, usando a mesma fabrica de corpo que o handler usa.
 */
@Component
// Antes de tudo, inclusive da cadeia do Spring Security: o 401 que ela escreve
// tambem precisa sair no idioma pedido, e ela roda cedo.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocaleResolutionFilter extends OncePerRequestFilter {

    private static final String PARAMETRO = "lang";

    /** Onde o idioma resolvido fica para o AppLocaleResolver e o RequestLocale lerem. */
    public static final String ATRIBUTO = AppLocale.class.getName();

    private final ProblemDetailFactory problemas;
    private final ObjectMapper json;
    private final AppLocale padrao;

    public LocaleResolutionFilter(
            ProblemDetailFactory problemas,
            ObjectMapper json,
            @Value("${app.default-locale:pt-BR}") String codigoPadrao) {
        this.problemas = problemas;
        this.json = json;
        this.padrao = AppLocale.porCodigo(codigoPadrao)
                .orElseThrow(() -> new IllegalStateException(
                        "APP_DEFAULT_LOCALE invalido: " + codigoPadrao + ". Use pt-BR ou en."));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain corrente)
            throws ServletException, IOException {

        String pedido = requisicao.getParameter(PARAMETRO);
        AppLocale idioma;

        if (pedido != null && !pedido.isBlank()) {
            Optional<AppLocale> explicito = AppLocale.porCodigo(pedido);
            if (explicito.isEmpty()) {
                recusar(requisicao, resposta, pedido);
                return;
            }
            idioma = explicito.get();
        } else {
            idioma = doHeader(requisicao.getHeader(HttpHeaders.ACCEPT_LANGUAGE)).orElse(padrao);
        }

        requisicao.setAttribute(ATRIBUTO, idioma);
        LocaleContextHolder.setLocale(idioma.locale());
        resposta.setHeader(HttpHeaders.CONTENT_LANGUAGE, idioma.codigo());
        try {
            corrente.doFilter(requisicao, resposta);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /** Primeiro idioma suportado por ordem de peso q; vazio se o header nao tiver nenhum. */
    private Optional<AppLocale> doHeader(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Locale.LanguageRange.parse(header).stream()
                    .map(faixa -> AppLocale.porLingua(faixa.getRange()))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (IllegalArgumentException header_malformado) {
            // Header quebrado nao derruba a requisicao: cai no padrao.
            return Optional.empty();
        }
    }

    private void recusar(HttpServletRequest requisicao, HttpServletResponse resposta, String recebido)
            throws IOException {

        // A recusa e escrita no idioma que o cliente pediu no header, ja que o
        // ?lang dele nao vale.
        LocaleContextHolder.setLocale(doHeader(requisicao.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
                .orElse(padrao)
                .locale());
        try {
            ProblemDetail corpo = problemas.montar(
                    ProblemTypes.UNSUPPORTED_LOCALE,
                    problemas.texto("problema.unsupported-locale.detalhe", recebido),
                    requisicao.getRequestURI(),
                    List.of());

            resposta.setStatus(ProblemTypes.UNSUPPORTED_LOCALE.status().value());
            resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            resposta.setCharacterEncoding("UTF-8");
            resposta.setHeader(
                    HttpHeaders.CONTENT_LANGUAGE, RequestLocale.atual().codigo());
            json.writeValue(resposta.getOutputStream(), corpo);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
