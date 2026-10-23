/**
 * Textos de interface em portugues. Esta e a fonte: o arquivo em ingles deriva
 * o tipo daqui, entao chave faltando la vira erro de compilacao, e nao texto em
 * branco em producao.
 *
 * Sem biblioteca de i18n: nesta fatia nao ha pluralizacao nem formatacao de
 * mensagem, e `Intl` cobre numero, moeda e data. Uma dependencia aqui seria
 * carregar um motor para interpolar duas strings.
 */
export const ptBR = {
  site: {
    nome: "Marketplace de SaaS",
    descricao: "Catálogo curado de soluções SaaS, em português e inglês.",
  },
  busca: {
    rotulo: "Buscar no catálogo",
    exemplo: "atendimento no WhatsApp, geração de conteúdo…",
    enviar: "Buscar",
    resultadosPara: (termo: string) => `Resultados para “${termo}”`,
    semTermo: "Digite o que você procura para ver os resultados.",
    // O aviso do fallback de trigram (spec secao 5.2): lista aproximada tem de
    // ser distinguivel de lista exata.
    aproximado: (termo: string) => `Nada exato para “${termo}” — mostrando o que mais se aproxima.`,
  },
  categoria: {
    // Funcao, e nao string com placeholder: pluralizacao e regra, e o tipo
    // obriga o ingles a implementar a dele.
    produtos: (n: number) => (n === 1 ? "1 produto" : `${n} produtos`),
    explorar: "Explore por categoria",
  },
  produto: {
    semPreco: "Preço sob consulta",
    aPartirDe: (preco: string, periodo: string) => `A partir de ${preco}${periodo}`,
    porMes: "/mês",
    porAno: "/ano",
    pagamentoUnico: " (pagamento único)",
    testeGratis: "Teste grátis",
    planoGratuito: "Plano gratuito",
  },
  comparacao: {
    adicionar: "Comparar",
    remover: "Remover da comparação",
    limite: (maximo: number) => `Máximo de ${maximo} produtos`,
    selecionados: (n: number) => (n === 1 ? "1 produto selecionado" : `${n} produtos selecionados`),
    ir: "Comparar agora",
    limpar: "Limpar seleção",
    titulo: "Comparação",
    faixa: (minimo: number, maximo: number) =>
      `Escolha de ${minimo} a ${maximo} produtos para comparar.`,
    ausentes: (slugs: string) => `Não encontramos: ${slugs}.`,
    seguirComOsQueRestaram: "Comparar os que restaram",
    atributo: {
      preco: "Preço",
      cobranca: "Modelo de cobrança",
      testeGratis: "Teste grátis",
      planoGratuito: "Plano gratuito",
      categoria: "Categoria",
      idiomas: "Idiomas",
      sede: "Sede",
      integracoes: "Integrações",
      melhorPara: "Melhor para",
      site: "Site",
    },
    sim: "Sim",
    nao: "Não",
  },
  ficha: {
    sobre: "Sobre",
    pontosFortes: "Pontos fortes",
    pontosFracos: "Pontos de atenção",
    melhorPara: "Melhor para",
    perguntas: "Perguntas frequentes",
    idiomas: "Idiomas",
    sede: "Sede",
    integracoes: "Integrações",
    tags: "Tags",
    visitarSite: "Visitar o site",
    atualizadoEm: (data: string) => `Atualizado em ${data}`,
    traducaoAusente: "O conteúdo curado deste produto ainda não está disponível em português.",
    verNoOutroIdioma: "Ver a versão em inglês",
  },
  reviews: {
    titulo: "Avaliações de quem usa",
    // Funcao, e nao string com placeholder: pluralizacao e regra, e o tipo
    // obriga o ingles a implementar a dele.
    contagem: (n: number) => (n === 1 ? "1 avaliação" : `${n} avaliações`),
    verificadas: (n: number) =>
      n === 1 ? "1 com e-mail corporativo verificado" : `${n} com e-mail corporativo verificado`,
    semNota: "Este produto ainda não tem avaliações publicadas.",
    // O que a pessoa que le precisa saber para interpretar o silencio: nao e
    // que o produto seja ruim, e que ninguem avaliou ainda.
    sejaOPrimeiro: "Seja quem escreve a primeira.",
    indisponivel:
      "Não foi possível carregar as avaliações agora. O resto da ficha continua válido.",
    selo: (dominio: string) => `Verificado · ${dominio}`,
    seloTitulo: "Esta pessoa confirmou um e-mail neste domínio",
    escritaEm: (idioma: string) => `Escrita em ${idioma}`,
    nota: (n: number) => `Nota ${n} de 5`,
    avaliar: "Avaliar este produto",
    politica: "Como funcionam as avaliações",
    paginacao: {
      navegacao: "Paginação das avaliações",
      anterior: "Avaliações mais recentes",
      proxima: "Avaliações mais antigas",
    },
  },
  avaliar: {
    titulo: (produto: string) => `Avaliar ${produto}`,
    // Quem escreve precisa saber, antes de escrever, que a avaliacao nao
    // aparece na hora: e o que evita a impressao de que ela sumiu (AC-18).
    comoFunciona:
      "Você recebe um link por e-mail para confirmar que o endereço é seu. Depois disso, uma pessoa da curadoria lê a avaliação antes de ela aparecer na página do produto.",
    nota: "Sua nota",
    notaOpcao: (n: number) => `${n} de 5`,
    recomendaria: "Recomendaria este produto?",
    sim: "Sim",
    nao: "Não",
    tituloDaReview: "Resumo em uma frase",
    corpo: "O que você tem a dizer",
    corpoAjuda: "No mínimo 80 caracteres — conte o que ajudaria alguém a decidir.",
    nome: "Nome de exibição",
    email: "E-mail de trabalho",
    emailAjuda:
      "Nunca aparece na página. Serve para confirmar o endereço e para o selo de verificado.",
    enviar: "Enviar avaliação",
    voltarParaFicha: "Voltar para a página do produto",
    politica: "Como funcionam as avaliações",
    resumoDeErros: "Confira os campos marcados abaixo.",
    erros: {
      obrigatorio: "Preencha este campo.",
      notaInvalida: "Escolha uma nota de 1 a 5.",
      corpoCurto: "Faltou texto: são no mínimo 80 caracteres.",
      corpoLongo: "Texto longo demais: o limite é 2.000 caracteres.",
      tituloLongo: "Resumo longo demais: o limite é 120 caracteres.",
      nomeLongo: "Nome longo demais: o limite é 80 caracteres.",
      emailInvalido: "Este endereço de e-mail não parece válido.",
    },
    falhas: {
      limite: "Você enviou avaliações demais em pouco tempo. Tente de novo em alguns minutos.",
      invalido: "Não foi possível aceitar esta avaliação. Revise os campos e tente de novo.",
      indisponivel:
        "Não conseguimos enviar agora. Seu texto continua aqui — tente de novo em instantes.",
    },
    emAnaliseTitulo: "Avaliação enviada",
    emAnalise:
      "Enviamos um link de confirmação para o seu e-mail. Depois que você confirmar, a avaliação passa pela curadoria — por isso ela ainda não aparece na página do produto.",
    emAnaliseSpam: "Não chegou? Confira a caixa de spam. O link vale por 48 horas.",
  },
  confirmacao: {
    tituloSucesso: "E-mail confirmado",
    sucesso:
      "Obrigado. Sua avaliação está na fila da curadoria e aparece na página do produto assim que for aprovada.",
    tituloExpirado: "Este link não vale mais",
    // Os tres casos ruins respondem igual de proposito (spec 003, AC-03):
    // distinguir "ja usado" de "nao existe" contaria a quem chuta tokens quais
    // deles um dia existiram.
    expirado:
      "Ele pode já ter sido usado ou ter passado das 48 horas de validade. Se a avaliação ainda não foi confirmada, podemos enviar um link novo.",
    tituloIndisponivel: "Não foi possível confirmar agora",
    indisponivel: "Tente novamente em alguns instantes. O link continua valendo.",
    reenviarRotulo: "Seu e-mail de trabalho",
    reenviarAjuda: "O mesmo que você usou ao escrever a avaliação.",
    reenviar: "Enviar um link novo",
    // A API responde 202 sempre, inclusive quando nao havia o que reenviar
    // (spec secao 5.1): a tela nao pode dizer mais do que isso.
    reenviado:
      "Se houver uma avaliação aguardando confirmação para este e-mail, o link novo já está a caminho.",
    reenvioIndisponivel: "Não conseguimos enviar agora. Tente de novo em instantes.",
    verProduto: "Ir para a página do produto",
    verCatalogo: "Ir para o catálogo",
  },
  politica: {
    titulo: "Como funcionam as avaliações",
    atualizadaEm: (data: string) => `Atualizada em ${data}`,
    quemPodeTitulo: "Quem pode avaliar",
    quemPode:
      "Qualquer pessoa que use ou tenha usado o produto. Não é preciso criar conta: você escreve, confirma o endereço de e-mail por um link e pronto. Cada pessoa pode avaliar um produto uma vez.",
    verificadoTitulo: "O que o selo de verificado significa",
    verificado:
      "Que a pessoa confirmou um e-mail em um domínio próprio — por exemplo, @suaempresa.com.br. Não significa que a avaliação seja melhor, mais confiável ou mais importante que as outras.",
    verificadoGratuito:
      "Avaliações escritas com e-mail de provedor gratuito (Gmail, Outlook, Yahoo e semelhantes) são aceitas e publicadas normalmente, apenas sem o selo. Bloqueá-las excluiria a pequena empresa que usa Gmail para tudo — que é exatamente quem este catálogo existe para atender.",
    moderacaoTitulo: "Toda avaliação passa por uma pessoa",
    moderacao:
      "Nada é publicado automaticamente. Alguém da curadoria lê cada avaliação antes de ela aparecer na página do produto. Toda decisão fica registrada, com quem decidiu, quando e por quê.",
    removidoTitulo: "O que é recusado ou removido",
    removidoItens: [
      "Texto sem relação com o produto avaliado.",
      "Ofensa, discurso de ódio ou ataque a pessoas.",
      "Dado pessoal de terceiros, seu ou de quem quer que seja.",
      "Avaliação escrita por quem tem interesse direto no produto — funcionário, sócio, concorrente — sem dizer isso.",
      "Avaliação incentivada por pagamento, brinde ou desconto, sem declaração explícita.",
      "Texto repetido, propaganda ou link comercial.",
    ],
    idiomaTitulo: "Avaliações não são traduzidas",
    idioma:
      "Cada avaliação existe no idioma em que foi escrita. Mostramos todas, marcando as que estão no outro idioma. Traduzir automaticamente seria colocar palavras na boca de quem escreveu.",
    contestacaoTitulo: "Contestação",
    contestacao: (endereco: string, prazo: number) =>
      `Se você representa um produto avaliado e considera que uma avaliação viola esta política, escreva para ${endereco}. Respondemos em até ${prazo} dias úteis, dizendo o que foi decidido e por quê.`,
    contestacaoLimite:
      "Uma avaliação não é removida por pedido de quem foi avaliado quando ela não viola esta política. Discordar do que foi dito não é motivo de remoção — a opinião de quem usa o produto é o que este catálogo existe para publicar.",
    dadosTitulo: "O que fazemos com o seu e-mail",
    dados:
      "Ele não aparece em nenhuma página. Serve para confirmar que o endereço é seu e para decidir o selo de verificado. Avaliação que nunca é confirmada é apagada em 30 dias, junto com o endereço.",
  },
  lista: {
    resultados: (n: number) => (n === 1 ? "1 produto" : `${n} produtos`),
    vazio: "Nenhum produto nesta categoria ainda.",
  },
  vazio: {
    comFiltro: "Nenhum produto com esses filtros.",
    filtrosAplicados: "Filtros aplicados",
    remover: (rotulo: string) => `Remover o filtro ${rotulo}`,
    alternativa: "Ou explore por categoria:",
  },
  paginacao: {
    navegacao: "Paginação",
    // Rotulo em base 1 para quem le; a URL segue em base 0, como a API.
    status: (pagina: number, total: number) => `Página ${pagina} de ${total}`,
    anterior: "Página anterior",
    proxima: "Próxima página",
  },
  filtro: {
    titulo: "Filtrar",
    integracoes: "Integrações",
    cobranca: "Modelo de cobrança",
    gratuidade: "Gratuidade",
    testeGratis: "Tem teste grátis",
    planoGratuito: "Tem plano gratuito",
    precoAte: "Preço até",
    aplicar: "Aplicar filtros",
    limparTudo: "Limpar filtros",
    cobrancaRotulo: {
      FREE: "Gratuito",
      FREEMIUM: "Freemium",
      SUBSCRIPTION: "Assinatura",
      USAGE_BASED: "Por uso",
      ONE_TIME: "Pagamento único",
      CONTACT_SALES: "Sob consulta",
    },
  },
  navegacao: {
    irParaHome: "Ir para a página inicial",
    trocarIdioma: "Ver em inglês",
  },
  erro: {
    naoEncontradoTitulo: "Não encontramos esta página",
    naoEncontradoTexto: "O endereço pode ter mudado, ou o produto não está mais publicado.",
    falhaTitulo: "Alguma coisa saiu errado",
    falhaTexto: "Tente de novo em instantes. Se continuar, o problema é nosso.",
    indisponivelTitulo: "O catálogo está fora do ar",
    indisponivelTexto:
      "Não conseguimos falar com o serviço que guarda os produtos. Já estamos vendo isso.",
    tentarDeNovo: "Tentar de novo",
  },
} as const;

/**
 * `typeof ptBR` sozinho NAO serve como contrato: com `as const`, cada texto vira
 * um tipo literal, e o ingles jamais satisfaria "Marketplace de SaaS". O mapa
 * abaixo troca os literais por `string` e preserva o formato das chaves - que e
 * a unica coisa que precisa ser igual nos dois idiomas.
 *
 * Descoberto tentando a versao ingenua: o `tsc` reclamava de texto traduzido,
 * e nao de chave faltando.
 */
type MesmasChaves<T> = {
  // Funcao passa como esta: `produtos(n)` e regra de pluralizacao, e cada
  // idioma implementa a sua. String vira `string` para o texto poder diferir.
  [K in keyof T]: T[K] extends (...args: infer A) => infer R
    ? (...args: A) => R
    : T[K] extends string
      ? string
      : MesmasChaves<T[K]>;
};

export type Messages = MesmasChaves<typeof ptBR>;
