import { paginaDeCategoria, metadataDeCategoria } from "@/src/paginas/categoria";

// Rota em pt-BR do par "categoria". A implementacao e uma so; este arquivo
// existe porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeCategoria("pt-BR");

// `generateMetadata` sai da mesma fabrica: a canonica e o par de hreflang
// dependem do idioma que esta rota serve.
export const generateMetadata = metadataDeCategoria("pt-BR");
