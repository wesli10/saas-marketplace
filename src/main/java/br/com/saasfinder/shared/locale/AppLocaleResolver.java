package br.com.saasfinder.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Faz o Spring MVC enxergar o idioma que o filtro ja resolveu.
 *
 * <p>Sem isto, o DispatcherServlet sobrescreve o LocaleContextHolder no inicio de
 * cada requisicao com o resultado do seu proprio resolvedor - que so olha
 * Accept-Language e ignora o parametro `lang`. O filtro acertava e o framework
 * desfazia logo em seguida.
 *
 * <p>O nome do bean precisa ser exatamente "localeResolver": e por ele que o
 * DispatcherServlet procura.
 */
@Component("localeResolver")
public class AppLocaleResolver implements LocaleResolver {

    private final AppLocale padrao;

    public AppLocaleResolver(@Value("${app.default-locale:pt-BR}") String codigoPadrao) {
        this.padrao = AppLocale.porCodigo(codigoPadrao).orElse(AppLocale.PT_BR);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest requisicao) {
        Object resolvido = requisicao.getAttribute(LocaleResolutionFilter.ATRIBUTO);
        return resolvido instanceof AppLocale idioma ? idioma.locale() : padrao.locale();
    }

    @Override
    public void setLocale(HttpServletRequest requisicao, HttpServletResponse resposta, Locale idioma) {
        throw new UnsupportedOperationException("O idioma vem da requisicao, nao de sessao ou cookie.");
    }
}
