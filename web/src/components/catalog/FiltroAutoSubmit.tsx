"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

/**
 * A melhoria progressiva do painel de filtros.
 *
 * Sem JavaScript o formulario ja funciona sozinho, e este componente nao existe
 * na pagina servida. Com JavaScript ele faz duas coisas: submete ao mudar
 * qualquer controle, e marca o formulario para o CSS esconder o botao
 * "aplicar", que passa a ser redundante.
 *
 * Marcar o formulario em vez de esconder o botao no servidor e o que mantem a
 * versao sem JS correta: o botao esta sempre no HTML, e some so depois que o
 * script roda.
 *
 * `replace` e nao `push`: cada clique de caixa criaria uma entrada de
 * historico, e voltar dez vezes para desmarcar dez caixas nao e navegacao, e
 * castigo.
 */
export function FiltroAutoSubmit({ formulario }: { formulario: string }) {
  const router = useRouter();

  useEffect(() => {
    const form = document.getElementById(formulario);
    if (!(form instanceof HTMLFormElement)) return;

    form.dataset.js = "ligado";

    const aoMudar = () => {
      const dados = new FormData(form);
      const query = new URLSearchParams();
      for (const [chave, valor] of dados.entries()) {
        const texto = String(valor).trim();
        if (texto !== "") query.append(chave, texto);
      }
      const destino = form.getAttribute("action") ?? "";
      router.replace(query.toString() === "" ? destino : `${destino}?${query}`);
    };

    form.addEventListener("change", aoMudar);
    return () => {
      form.removeEventListener("change", aoMudar);
      delete form.dataset.js;
    };
  }, [formulario, router]);

  return null;
}
