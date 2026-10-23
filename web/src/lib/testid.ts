/**
 * Ids de teste, num lugar so.
 *
 * O componente e o teste leem a mesma constante: id escrito a mao nos dois
 * lados diverge no primeiro rename, e o teste passa a falhar por um motivo que
 * nao tem nada a ver com o que ele deveria provar.
 *
 * Convencao (constituicao secao 6):
 *  - kebab-case, do escopo para o detalhe;
 *  - nunca derivado de texto traduzivel nem de dado do seed - a interface e
 *    bilingue, entao id vindo do texto quebra ao trocar de idioma;
 *  - item de lista usa id de tipo (`produto-card`) mais um atributo que diz
 *    qual (`data-slug`), e nao um id por produto.
 */
export const TESTID = {
  // Lista e card de produto
  produtoCard: "produto-card",
  produtoPreco: "produto-preco",
  produtoLogo: "produto-logo",
  produtoBadge: "produto-badge",
  listaTitulo: "lista-titulo",
  listaContagem: "lista-contagem",
  listaVazia: "lista-vazia",
  vazioRemoverFiltro: "vazio-remover-filtro",
  vazioLimparTudo: "vazio-limpar-tudo",
  vazioCategoria: "vazio-categoria",

  // Bloco de reviews na ficha (spec 003 secao 6)
  reviewsBloco: "reviews-bloco",
  reviewsNota: "reviews-nota",
  reviewsMedia: "reviews-media",
  reviewsContagem: "reviews-contagem",
  reviewsVerificadas: "reviews-verificadas",
  reviewsVazio: "reviews-vazio",
  reviewsIndisponivel: "reviews-indisponivel",
  reviewsPolitica: "reviews-politica",
  reviewCard: "review-card",
  reviewTitulo: "review-titulo",
  reviewCorpo: "review-corpo",
  reviewAutor: "review-autor",
  reviewNota: "review-nota",
  reviewSelo: "review-selo",
  reviewIdioma: "review-idioma",
  reviewsPaginacao: "reviews-paginacao",
  reviewsPaginacaoAnterior: "reviews-paginacao-anterior",
  reviewsPaginacaoProxima: "reviews-paginacao-proxima",

  // Formulario de avaliacao (spec 003 secao 6)
  avaliarFormulario: "avaliar-formulario",
  avaliarNota: "avaliar-nota",
  avaliarNotaOpcao: "avaliar-nota-opcao",
  avaliarRecomendaria: "avaliar-recomendaria",
  avaliarRecomendariaOpcao: "avaliar-recomendaria-opcao",
  avaliarTitulo: "avaliar-titulo",
  avaliarCorpo: "avaliar-corpo",
  avaliarNome: "avaliar-nome",
  avaliarEmail: "avaliar-email",
  avaliarEnviar: "avaliar-enviar",
  avaliarErro: "avaliar-erro",
  avaliarFalha: "avaliar-falha",
  avaliarResumoDeErros: "avaliar-resumo-de-erros",
  avaliarEmAnalise: "avaliar-em-analise",
  avaliarVoltar: "avaliar-voltar",
  fichaAvaliar: "ficha-avaliar",

  // Confirmacao de e-mail (spec 003 secao 6)
  confirmacaoSucesso: "confirmacao-sucesso",
  confirmacaoExpirado: "confirmacao-expirado",
  confirmacaoIndisponivel: "confirmacao-indisponivel",
  confirmacaoReenvioFormulario: "confirmacao-reenvio-formulario",
  confirmacaoReenvioEmail: "confirmacao-reenvio-email",
  confirmacaoReenvioEnviar: "confirmacao-reenvio-enviar",
  confirmacaoReenviado: "confirmacao-reenviado",
  confirmacaoVerProduto: "confirmacao-ver-produto",

  // Politica de reviews (spec 003 secao 6 e 7.1)
  politicaTitulo: "politica-titulo",
  politicaContestacao: "politica-contestacao",
  politicaContestacaoEndereco: "politica-contestacao-endereco",
  politicaLimiteDeRemocao: "politica-limite-de-remocao",
  politicaRemovido: "politica-removido",

  // Comparacao
  compararControle: "comparar-controle",
  compararLimite: "comparar-limite",
  compararBarra: "comparar-barra",
  compararIr: "comparar-ir",
  compararLimpar: "comparar-limpar",
  comparacaoTabela: "comparacao-tabela",
  comparacaoColuna: "comparacao-coluna",
  comparacaoLinha: "comparacao-linha",
  comparacaoFaixa: "comparacao-faixa",
  comparacaoAusentes: "comparacao-ausentes",
  comparacaoSeguir: "comparacao-seguir",

  // Ficha do produto
  fichaTitulo: "ficha-titulo",
  fichaTagline: "ficha-tagline",
  fichaCategoria: "ficha-categoria",
  fichaPreco: "ficha-preco",
  fichaCobranca: "ficha-cobranca",
  fichaSite: "ficha-site",
  fichaDescricao: "ficha-descricao",
  fichaPro: "ficha-pro",
  fichaCon: "ficha-con",
  fichaMelhorPara: "ficha-melhor-para",
  fichaFaqItem: "ficha-faq-item",
  fichaIdiomas: "ficha-idiomas",
  fichaPais: "ficha-pais",
  fichaIntegracoes: "ficha-integracoes",
  fichaTags: "ficha-tags",
  fichaAtualizado: "ficha-atualizado",
  fichaTraducaoAusente: "ficha-traducao-ausente",
  fichaOutraVersao: "ficha-outra-versao",

  // Filtros
  filtroPainel: "filtro-painel",
  filtroIntegracao: "filtro-integracao",
  filtroCobranca: "filtro-cobranca",
  filtroTesteGratis: "filtro-teste-gratis",
  filtroPlanoGratuito: "filtro-plano-gratuito",
  filtroPrecoMax: "filtro-preco-max",
  filtroAplicar: "filtro-aplicar",
  filtroLimpar: "filtro-limpar",

  // Paginacao
  paginacao: "paginacao",
  paginacaoAnterior: "paginacao-anterior",
  paginacaoProxima: "paginacao-proxima",
  paginacaoStatus: "paginacao-status",

  // Home
  homeTitulo: "home-titulo",
  buscaFormulario: "busca-formulario",
  buscaCampo: "busca-campo",
  buscaEnviar: "busca-enviar",
  buscaAproximada: "busca-aproximada",
  buscaSemTermo: "busca-sem-termo",
  categoriaRaiz: "categoria-raiz",
  categoriaCard: "categoria-card",
  categoriaContagem: "categoria-contagem",
  paginaNaoEncontrada: "pagina-nao-encontrada",
  paginaDeErro: "pagina-de-erro",
  tentarDeNovo: "tentar-de-novo",
  linkHome: "link-home",
  seletorIdioma: "seletor-idioma",
} as const;

export type Testid = (typeof TESTID)[keyof typeof TESTID];
