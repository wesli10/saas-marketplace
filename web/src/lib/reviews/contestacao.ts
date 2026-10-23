import "server-only";

/**
 * O canal de contestação da política de reviews (spec 003 secao 7.1).
 *
 * Vem do ambiente, e nao escrito no codigo, por dois motivos. O primeiro e que
 * endereco de contato muda sem que nada no produto mude. O segundo e mais
 * importante: **este e um compromisso publico**, e quem o assume e quem sobe o
 * ambiente - inventar um endereco aqui seria publicar uma promessa que ninguem
 * fez (constituicao secao 7).
 *
 * O padrao usa `example.com`, que a IANA reserva para documentacao: se ele
 * aparecer em producao, e obviamente um valor por preencher, e nao um endereco
 * de verdade que ninguem le.
 */
export const CONTATO_DE_CONTESTACAO = process.env.REVIEWS_CONTATO ?? "contestacao@example.com";

/** Prazo de resposta, em dias uteis. Igualmente um compromisso, e configuravel. */
export const PRAZO_DE_RESPOSTA = Number(process.env.REVIEWS_PRAZO_DIAS ?? 10);
