package br.com.saasfinder.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador do Spring (spec 003, secao 4.2: o expurgo das reviews nao
 * confirmadas).
 *
 * <p>Fica numa classe propria, e nao na anotacao da aplicacao, para que o motivo
 * de existir um agendador esteja escrito em algum lugar. Hoje ha uma tarefa so; se
 * um dia houver duas instancias, uma delas vai duplicar a execucao - o expurgo
 * tolera isso (deletar o que ja foi deletado nao faz nada), mas a proxima tarefa
 * agendada talvez nao tolere, e ai e hora de um agendador de verdade.
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {}
