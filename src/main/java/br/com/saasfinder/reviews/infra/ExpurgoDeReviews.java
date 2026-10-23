package br.com.saasfinder.reviews.infra;

import br.com.saasfinder.reviews.domain.ReviewService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda o expurgo das reviews que nunca foram confirmadas (spec secao 4.2).
 *
 * <p>De madrugada e uma vez por dia: sao poucas linhas, e o unico requisito e que
 * nada fique guardado alem dos 30 dias. O horario e configuravel para que um
 * ambiente possa desligar a rotina sem mexer no codigo.
 *
 * <p>A classe existe so para separar <b>quando</b> de <b>o que</b>: a regra vive
 * no {@link ReviewService}, que o teste chama direto, sem esperar o relogio.
 */
@Component
public class ExpurgoDeReviews {

    private final ReviewService reviews;

    public ExpurgoDeReviews(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Scheduled(cron = "${app.reviews.expurgo-cron}")
    public void expurgar() {
        reviews.expurgarNaoConfirmadas();
    }
}
