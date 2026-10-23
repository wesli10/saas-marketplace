import { paginaDeProduto, metadataDeProduto } from "@/src/paginas/produto";

// Rota em en do par "produto". A implementacao e uma so; este arquivo existe
// porque o App Router precisa de um caminho por segmento traduzido.
export default paginaDeProduto("en");

// `generateMetadata` sai da mesma fabrica: a canonica e o par de hreflang
// dependem do idioma que esta rota serve.
export const generateMetadata = metadataDeProduto("en");
