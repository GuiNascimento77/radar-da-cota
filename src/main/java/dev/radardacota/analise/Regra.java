package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import java.util.List;

/**
 * Uma pergunta sobre os dados.
 *
 * <p>Cada regra olha a lista inteira de despesas do ano e devolve os casos que
 * respondem "sim" para a pergunta dela. Nenhuma regra conhece as outras: para
 * acrescentar uma pergunta nova, basta escrever uma classe que implemente esta
 * interface e registrá-la em {@link Analisador}.
 */
public interface Regra {

    /** Identificador curto, sem acento e sem espaço: aparece no JSON e na URL. */
    String nome();

    /** Nome legível, para a etiqueta do alerta na tela. */
    String titulo();

    /** A pergunta, em uma frase, para a tela "Como funciona". */
    String pergunta();

    /** O que sustenta o alerta e onde está o limite de cada um. */
    String criterio();

    List<Alerta> analisar(List<Despesa> despesas);
}
