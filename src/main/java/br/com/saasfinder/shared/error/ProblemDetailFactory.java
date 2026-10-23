package br.com.saasfinder.shared.error;

import br.com.saasfinder.shared.locale.RequestLocale;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Monta o corpo RFC 9457.
 *
 * <p>Existe como componente proprio porque dois lugares precisam dele: o
 * {@link GlobalExceptionHandler} e o filtro de idioma, que recusa a requisicao
 * antes de qualquer controller - e portanto antes de haver quem trate a excecao.
 */
@Component
public class ProblemDetailFactory {

    private final MessageSource mensagens;

    public ProblemDetailFactory(MessageSource mensagens) {
        this.mensagens = mensagens;
    }

    public ProblemDetail montar(ProblemType tipo, String detalhe, String caminho, List<ApiFieldError> erros) {
        return montar(tipo, detalhe, caminho, erros, Map.of());
    }

    public ProblemDetail montar(
            ProblemType tipo,
            String detalhe,
            String caminho,
            List<ApiFieldError> erros,
            Map<String, Object> extensoes) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(tipo.status(), detalhe);
        corpo.setType(tipo.uri());
        corpo.setTitle(texto(tipo.chaveTitulo()));
        corpo.setInstance(URI.create(caminho));

        if (!erros.isEmpty()) {
            corpo.setProperty("errors", erros);
        }
        // As chaves vao literais, em snake_case: propriedade de ProblemDetail e mapa,
        // e a estrategia de nomes do Jackson nao renomeia chave de mapa.
        extensoes.forEach(corpo::setProperty);
        return corpo;
    }

    /**
     * Traduz pela decisao do filtro de idioma, e nao pelo LocaleContextHolder.
     *
     * <p>O holder nao e confiavel aqui: o RequestContextFilter do Spring o
     * sobrescreve com request.getLocale(), que so enxerga Accept-Language e ignora
     * o parametro `lang`. Isso fazia o 401 sair em ingles numa requisicao
     * ?lang=pt-BR, mesmo com o Content-Language correto no cabecalho.
     */
    public String texto(String chave, Object... argumentos) {
        return mensagens.getMessage(
                chave, argumentos, chave, RequestLocale.atual().locale());
    }
}
