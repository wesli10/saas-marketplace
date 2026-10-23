import { paginaDeBusca, metadataDeBusca } from "@/src/paginas/busca";

// Rota em pt-BR do par "busca". A implementacao e uma so; este arquivo existe
// porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeBusca("pt-BR");

export const metadata = metadataDeBusca("pt-BR");
