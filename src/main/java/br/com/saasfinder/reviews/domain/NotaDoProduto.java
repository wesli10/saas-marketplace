package br.com.saasfinder.reviews.domain;

import java.math.BigDecimal;

/**
 * O agregado de reviews publicadas de um produto (spec secao 4.4).
 *
 * <p>Calculado na consulta, sem coluna denormalizada: com 20 produtos e dezenas de
 * reviews o custo e irrelevante, e coluna mantida por gatilho seria uma segunda
 * fonte da verdade, que diverge no primeiro UPDATE esquecido.
 *
 * <p>Produto sem review publicada nao tem nota - e {@code null} no corpo, e nao
 * este record zerado. Nota zero e uma avaliacao pessima; ausencia de nota e outra
 * coisa.
 *
 * @param average media das notas, com uma casa decimal
 * @param count quantas reviews publicadas
 * @param recommendRatio proporcao de "recomendaria: sim", de 0 a 1
 * @param verifiedCount quantas tem o selo de verificada
 */
public record NotaDoProduto(BigDecimal average, long count, BigDecimal recommendRatio, long verifiedCount) {}
