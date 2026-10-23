import { paginaDeComparacao, metadataDaComparacao } from "@/src/paginas/comparacao";

// Rota em pt-BR do par "comparacao". A implementacao e uma so; este arquivo existe
// porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeComparacao("pt-BR");

export const metadata = metadataDaComparacao("pt-BR");
