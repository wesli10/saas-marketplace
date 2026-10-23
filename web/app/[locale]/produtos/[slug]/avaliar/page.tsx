import { paginaDeAvaliar, metadataDeAvaliar } from "@/src/paginas/avaliar";

// Rota em pt-BR do par "avaliar". A implementacao e uma so; este arquivo existe
// porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeAvaliar("pt-BR");

export const generateMetadata = metadataDeAvaliar("pt-BR");
