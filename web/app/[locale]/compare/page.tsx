import { paginaDeComparacao, metadataDaComparacao } from "@/src/paginas/comparacao";

// Rota em en do par "comparacao". A implementacao e uma so; este arquivo existe
// porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeComparacao("en");

export const metadata = metadataDaComparacao("en");
