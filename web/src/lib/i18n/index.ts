import type { AppLocale } from "./locales";
import { en } from "./messages.en";
import { ptBR, type Messages } from "./messages.pt-BR";

const CATALOGOS: Record<AppLocale, Messages> = { "pt-BR": ptBR, en };

export function textos(locale: AppLocale): Messages {
  return CATALOGOS[locale];
}

export type { Messages };
