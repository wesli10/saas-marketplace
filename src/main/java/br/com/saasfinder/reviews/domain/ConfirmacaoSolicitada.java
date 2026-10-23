package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.shared.locale.AppLocale;
import java.util.UUID;

/**
 * Ha um link de confirmacao para entregar - porque a review acabou de ser escrita,
 * ou porque alguem pediu o reenvio.
 *
 * <p>Nasceu como {@code ReviewCriada} na T04 e mudou de nome na T05, quando o
 * reenvio passou a publicar o mesmo evento: o que dispara o e-mail nao e a
 * criacao, e a existencia de um token novo esperando ser usado.
 *
 * <p>O <b>token em claro viaja no evento</b>, e nao no banco (plano DT-04): quem
 * envia o e-mail precisa dele, e ele nao pode ficar guardado em lugar nenhum.
 * Como o evento e consumido depois do commit e dentro do mesmo processo, ele nao
 * atravessa fronteira nenhuma.
 */
public record ConfirmacaoSolicitada(
        UUID reviewId,
        String destinatario,
        AppLocale idioma,
        String nomeDoAutor,
        String nomeDoProduto,
        /**
         * O slug vai junto porque o link precisa dele: a tela de confirmacao
         * oferece reenvio quando o token expirou, e o reenvio pergunta por
         * produto e e-mail (spec secao 5.1). Sem o slug no endereco, a pessoa
         * teria de dizer a mao qual produto avaliou.
         */
        String slugDoProduto,
        String tokenEmClaro) {}
