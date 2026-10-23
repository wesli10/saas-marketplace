import type { Messages } from "./messages.pt-BR";

/**
 * O tipo vem do portugues de proposito: `Messages` obriga este objeto a ter
 * exatamente as mesmas chaves. Remover uma quebra o `npm run typecheck`, que e
 * o unico jeito de a paridade nao depender de alguem conferir.
 */
export const en: Messages = {
  site: {
    nome: "SaaS Marketplace",
    descricao: "A curated catalog of SaaS tools, in Portuguese and English.",
  },
  busca: {
    rotulo: "Search the catalog",
    exemplo: "customer support on WhatsApp, content generation…",
    enviar: "Search",
    resultadosPara: (termo: string) => `Results for “${termo}”`,
    semTermo: "Type what you are looking for to see results.",
    aproximado: (termo: string) => `Nothing exact for “${termo}” — showing the closest matches.`,
  },
  categoria: {
    produtos: (n: number) => (n === 1 ? "1 product" : `${n} products`),
    explorar: "Browse by category",
  },
  produto: {
    semPreco: "Price on request",
    aPartirDe: (preco: string, periodo: string) => `From ${preco}${periodo}`,
    porMes: "/month",
    porAno: "/year",
    pagamentoUnico: " (one-time)",
    testeGratis: "Free trial",
    planoGratuito: "Free plan",
  },
  comparacao: {
    adicionar: "Compare",
    remover: "Remove from comparison",
    limite: (maximo: number) => `Up to ${maximo} products`,
    selecionados: (n: number) => (n === 1 ? "1 product selected" : `${n} products selected`),
    ir: "Compare now",
    limpar: "Clear selection",
    titulo: "Comparison",
    faixa: (minimo: number, maximo: number) => `Pick ${minimo} to ${maximo} products to compare.`,
    ausentes: (slugs: string) => `We could not find: ${slugs}.`,
    seguirComOsQueRestaram: "Compare the remaining ones",
    atributo: {
      preco: "Price",
      cobranca: "Pricing model",
      testeGratis: "Free trial",
      planoGratuito: "Free plan",
      categoria: "Category",
      idiomas: "Languages",
      sede: "Headquarters",
      integracoes: "Integrations",
      melhorPara: "Best for",
      site: "Website",
    },
    sim: "Yes",
    nao: "No",
  },
  ficha: {
    sobre: "About",
    pontosFortes: "Strengths",
    pontosFracos: "Things to watch",
    melhorPara: "Best for",
    perguntas: "Frequently asked questions",
    idiomas: "Languages",
    sede: "Headquarters",
    integracoes: "Integrations",
    tags: "Tags",
    visitarSite: "Visit the website",
    atualizadoEm: (data: string) => `Updated on ${data}`,
    traducaoAusente: "The curated content for this product is not available in English yet.",
    verNoOutroIdioma: "See the Portuguese version",
  },
  reviews: {
    titulo: "Reviews from people who use it",
    contagem: (n: number) => (n === 1 ? "1 review" : `${n} reviews`),
    verificadas: (n: number) =>
      n === 1 ? "1 with a verified work email" : `${n} with a verified work email`,
    semNota: "This product has no published reviews yet.",
    sejaOPrimeiro: "Be the one who writes the first.",
    indisponivel: "We could not load the reviews right now. The rest of the page is still valid.",
    selo: (dominio: string) => `Verified · ${dominio}`,
    seloTitulo: "This person confirmed an email address on this domain",
    escritaEm: (idioma: string) => `Written in ${idioma}`,
    nota: (n: number) => `Rated ${n} out of 5`,
    avaliar: "Review this product",
    politica: "How reviews work",
    paginacao: {
      navegacao: "Review pagination",
      anterior: "Newer reviews",
      proxima: "Older reviews",
    },
  },
  avaliar: {
    titulo: (produto: string) => `Review ${produto}`,
    comoFunciona:
      "You get a link by email to confirm the address is yours. After that, someone from our curation team reads the review before it shows up on the product page.",
    nota: "Your rating",
    notaOpcao: (n: number) => `${n} out of 5`,
    recomendaria: "Would you recommend this product?",
    sim: "Yes",
    nao: "No",
    tituloDaReview: "One-sentence summary",
    corpo: "What you have to say",
    corpoAjuda: "At least 80 characters — tell us what would help someone decide.",
    nome: "Display name",
    email: "Work email",
    emailAjuda:
      "Never shown on the page. It is used to confirm the address and for the verified badge.",
    enviar: "Send review",
    voltarParaFicha: "Back to the product page",
    politica: "How reviews work",
    resumoDeErros: "Check the fields marked below.",
    erros: {
      obrigatorio: "Please fill in this field.",
      notaInvalida: "Pick a rating from 1 to 5.",
      corpoCurto: "Too short: at least 80 characters.",
      corpoLongo: "Too long: the limit is 2,000 characters.",
      tituloLongo: "Summary too long: the limit is 120 characters.",
      nomeLongo: "Name too long: the limit is 80 characters.",
      emailInvalido: "That email address does not look valid.",
    },
    falhas: {
      limite: "You have sent too many reviews in a short time. Try again in a few minutes.",
      invalido: "We could not accept this review. Please check the fields and try again.",
      indisponivel:
        "We could not send it right now. Your text is still here — try again in a moment.",
    },
    emAnaliseTitulo: "Review sent",
    emAnalise:
      "We sent a confirmation link to your email. Once you confirm, the review goes through curation — that is why it is not on the product page yet.",
    emAnaliseSpam: "Did not arrive? Check your spam folder. The link is valid for 48 hours.",
  },
  confirmacao: {
    tituloSucesso: "Email confirmed",
    sucesso:
      "Thank you. Your review is in the curation queue and shows up on the product page once it is approved.",
    tituloExpirado: "This link is no longer valid",
    expirado:
      "It may already have been used, or it may have passed the 48-hour window. If the review has not been confirmed yet, we can send a new link.",
    tituloIndisponivel: "We could not confirm it right now",
    indisponivel: "Please try again in a moment. The link is still valid.",
    reenviarRotulo: "Your work email",
    reenviarAjuda: "The same one you used when writing the review.",
    reenviar: "Send a new link",
    reenviado:
      "If there is a review waiting for confirmation for this email, the new link is on its way.",
    reenvioIndisponivel: "We could not send it right now. Please try again in a moment.",
    verProduto: "Go to the product page",
    verCatalogo: "Go to the catalogue",
  },
  politica: {
    titulo: "How reviews work",
    atualizadaEm: (data: string) => `Updated on ${data}`,
    quemPodeTitulo: "Who can write a review",
    quemPode:
      "Anyone who uses or has used the product. No account needed: you write it, confirm your email address through a link, and that is it. Each person can review a product once.",
    verificadoTitulo: "What the verified badge means",
    verificado:
      "That the person confirmed an email address on their own domain — for example, @yourcompany.com. It does not mean the review is better, more trustworthy or more important than the others.",
    verificadoGratuito:
      "Reviews written with a free email provider (Gmail, Outlook, Yahoo and the like) are accepted and published normally, just without the badge. Blocking them would exclude the small business that runs everything on Gmail — exactly who this catalogue exists to serve.",
    moderacaoTitulo: "Every review is read by a person",
    moderacao:
      "Nothing is published automatically. Someone from our curation team reads every review before it appears on the product page. Every decision is recorded, with who decided, when and why.",
    removidoTitulo: "What gets rejected or removed",
    removidoItens: [
      "Text unrelated to the product being reviewed.",
      "Insults, hate speech or attacks on people.",
      "Personal data about third parties, yourself or anyone else.",
      "Reviews written by someone with a direct interest in the product — employee, partner, competitor — without saying so.",
      "Reviews incentivised by payment, gift or discount, without an explicit disclosure.",
      "Repeated text, advertising or commercial links.",
    ],
    idiomaTitulo: "Reviews are not translated",
    idioma:
      "Each review exists in the language it was written in. We show all of them, marking the ones written in the other language. Translating automatically would put words in someone else's mouth.",
    contestacaoTitulo: "Disputing a review",
    contestacao: (endereco: string, prazo: number) =>
      `If you represent a reviewed product and believe a review breaks this policy, write to ${endereco}. We reply within ${prazo} business days, saying what was decided and why.`,
    contestacaoLimite:
      "A review is not removed at the request of the company being reviewed when it does not break this policy. Disagreeing with what was said is not grounds for removal — the opinion of people who use the product is what this catalogue exists to publish.",
    dadosTitulo: "What we do with your email",
    dados:
      "It never appears on any page. It is used to confirm the address is yours and to decide the verified badge. A review that is never confirmed is deleted after 30 days, along with the address.",
  },
  lista: {
    resultados: (n: number) => (n === 1 ? "1 product" : `${n} products`),
    vazio: "No products in this category yet.",
  },
  vazio: {
    comFiltro: "No products match these filters.",
    filtrosAplicados: "Applied filters",
    remover: (rotulo: string) => `Remove the ${rotulo} filter`,
    alternativa: "Or browse by category:",
  },
  paginacao: {
    navegacao: "Pagination",
    status: (pagina: number, total: number) => `Page ${pagina} of ${total}`,
    anterior: "Previous page",
    proxima: "Next page",
  },
  filtro: {
    titulo: "Filter",
    integracoes: "Integrations",
    cobranca: "Pricing model",
    gratuidade: "Free options",
    testeGratis: "Has a free trial",
    planoGratuito: "Has a free plan",
    precoAte: "Price up to",
    aplicar: "Apply filters",
    limparTudo: "Clear filters",
    cobrancaRotulo: {
      FREE: "Free",
      FREEMIUM: "Freemium",
      SUBSCRIPTION: "Subscription",
      USAGE_BASED: "Usage-based",
      ONE_TIME: "One-time",
      CONTACT_SALES: "Contact sales",
    },
  },
  navegacao: {
    irParaHome: "Go to the home page",
    trocarIdioma: "View in Portuguese",
  },
  erro: {
    naoEncontradoTitulo: "We could not find this page",
    naoEncontradoTexto: "The address may have changed, or the product is no longer published.",
    falhaTitulo: "Something went wrong",
    falhaTexto: "Try again in a moment. If it keeps happening, the problem is on our side.",
    indisponivelTitulo: "The catalog is unavailable",
    indisponivelTexto:
      "We could not reach the service that holds the products. We are looking into it.",
    tentarDeNovo: "Try again",
  },
};
