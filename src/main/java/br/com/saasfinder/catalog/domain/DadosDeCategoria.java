package br.com.saasfinder.catalog.domain;

import java.util.UUID;

/**
 * Dados de entrada para criar ou atualizar uma categoria.
 *
 * <p>E um tipo do proprio catalog, e nao o DTO da borda: assim o modulo admin
 * depende do catalog, e nao o contrario - o formato do corpo HTTP pode mudar sem
 * mexer nas regras.
 *
 * <p>Na atualizacao os slugs sao opcionais: eles nao mudam depois da criacao
 * (spec secao 4.1). Vindo preenchidos e diferentes, a operacao e recusada em vez
 * de ignorada em silencio.
 */
public record DadosDeCategoria(
        String slugPt,
        String slugEn,
        String namePt,
        String nameEn,
        String descriptionPt,
        String descriptionEn,
        UUID parentId,
        Integer sortOrder) {}
