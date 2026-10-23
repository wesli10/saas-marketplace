package br.com.saasfinder.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.shared.id.IdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio de verificacao da tarefa 003-T01: a maquina de estados do plano DT-03,
 * exercitada sem banco e sem Spring - ela e regra de dominio puro.
 *
 * <p>Cada transicao invalida tem um teste proprio. Sao elas que justificam o
 * {@code enum} no lugar de dois booleanos: com booleanos, "publicada sem
 * confirmar" seria apenas uma combinacao esquecida, e nao um erro.
 */
class ReviewTransicoesTest {

    private static Review nova() {
        return new Review(
                IdGenerator.novo(),
                4,
                true,
                "Resolveu o atendimento",
                "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade.",
                "pt-BR",
                "Ana Ribeiro",
                "ana@acme.com.br");
    }

    @Test
    @DisplayName("nasce em PENDING_EMAIL, sem selo e sem carimbos")
    void nasceAguardandoEmail() {
        Review review = nova();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        assertThat(review.isVerified()).isFalse();
        assertThat(review.getConfirmedAt()).isNull();
        assertThat(review.getPublishedAt()).isNull();
        assertThat(review.getModeratedBy()).isNull();
    }

    @Test
    @DisplayName("o dominio sai do e-mail, e o e-mail e guardado em minusculas")
    void normalizaEmailEDerivaDominio() {
        Review review = new Review(
                IdGenerator.novo(),
                5,
                true,
                "Otimo",
                "Texto com folga suficiente para passar do piso de oitenta caracteres exigido pela spec 003.",
                "pt-BR",
                "Ana",
                "  Ana.Ribeiro@ACME.com.BR ");

        assertThat(review.getAuthorEmail()).isEqualTo("ana.ribeiro@acme.com.br");
        assertThat(review.getAuthorDomain()).isEqualTo("acme.com.br");
    }

    @Test
    @DisplayName("confirmar leva a PENDING_MODERATION e concede o selo ao dominio proprio")
    void confirmarConcedeOSelo() {
        Review review = nova();

        review.confirmar(false);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(review.getConfirmedAt()).isNotNull();
        assertThat(review.isVerified()).isTrue();
    }

    @Test
    @DisplayName("dominio de provedor gratuito confirma, mas fica sem selo")
    void gratuitoConfirmaSemSelo() {
        Review review = nova();

        review.confirmar(true);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(review.getConfirmedAt()).isNotNull();
        assertThat(review.isVerified()).isFalse();
    }

    @Test
    @DisplayName("confirmar duas vezes e transicao invalida")
    void confirmarDuasVezes() {
        Review review = nova();
        review.confirmar(false);

        assertThatThrownBy(() -> review.confirmar(false))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("problema.review.transicao-invalida");
    }

    @Test
    @DisplayName("publicar sem confirmar e transicao invalida")
    void publicarSemConfirmar() {
        Review review = nova();

        assertThatThrownBy(() -> review.publicar("curador")).isInstanceOf(TransicaoInvalidaException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        assertThat(review.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("rejeitar sem confirmar e transicao invalida")
    void rejeitarSemConfirmar() {
        Review review = nova();

        assertThatThrownBy(() -> review.rejeitar("fora da politica", "curador"))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("publicar grava quem decidiu e quando (AC-21c)")
    void publicarRegistraADecisao() {
        Review review = nova();
        review.confirmar(false);

        review.publicar("curador@saasfinder");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(review.getPublishedAt()).isNotNull();
        assertThat(review.getModeratedBy()).isEqualTo("curador@saasfinder");
        assertThat(review.getModeratedAt()).isNotNull();
    }

    @Test
    @DisplayName("rejeitar exige motivo, e o motivo fica registrado")
    void rejeitarExigeMotivo() {
        Review review = nova();
        review.confirmar(false);

        assertThatThrownBy(() -> review.rejeitar("   ", "curador")).isInstanceOf(IllegalArgumentException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);

        review.rejeitar("texto sem relacao com o produto", "curador");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(review.getModerationNote()).isEqualTo("texto sem relacao com o produto");
        assertThat(review.getModeratedBy()).isEqualTo("curador");
        assertThat(review.getModeratedAt()).isNotNull();
    }

    @Test
    @DisplayName("rejeitar review ja publicada e transicao invalida")
    void rejeitarPublicada() {
        Review review = nova();
        review.confirmar(false);
        review.publicar("curador");

        assertThatThrownBy(() -> review.rejeitar("mudei de ideia", "curador"))
                .isInstanceOf(TransicaoInvalidaException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publicar review ja rejeitada e transicao invalida: rejeitada nao volta")
    void publicarRejeitada() {
        Review review = nova();
        review.confirmar(false);
        review.rejeitar("fora da politica", "curador");

        assertThatThrownBy(() -> review.publicar("outro curador")).isInstanceOf(TransicaoInvalidaException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
    }
}
