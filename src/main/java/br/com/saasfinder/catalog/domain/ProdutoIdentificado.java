package br.com.saasfinder.catalog.domain;

import java.util.UUID;

/**
 * O minimo que outro modulo precisa saber de um produto publicado: quem ele e.
 *
 * <p>Existe para o modulo {@code reviews} (plano 003, DT-01), que precisa do id
 * para gravar a review e do nome para escrever o e-mail - e que <b>nao</b> pode
 * tocar no {@code ProductRepository} nem receber a entidade {@code Product}
 * (constituicao secao 4). A {@code ProductDetailView} nao serve: ela nao carrega
 * o id, e traz a ficha inteira resolvida num idioma.
 */
public record ProdutoIdentificado(UUID id, String slug, String name) {}
