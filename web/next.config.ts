import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // O artefato de producao e um container proprio (spec secao 8.2), entao o
  // build precisa carregar so o necessario: `standalone` gera um server.js com
  // as dependencias resolvidas, e a imagem final nao leva node_modules inteiro.
  output: "standalone",
};

export default nextConfig;
