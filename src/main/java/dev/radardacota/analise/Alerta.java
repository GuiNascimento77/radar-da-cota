package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import java.util.List;

/**
 * Um caso fora do padrão que o radar encontrou.
 *
 * <p>{@code gravidade} vai de 1 a 3 e {@code centavos} é quanto está em jogo.
 * A ordenação usa os dois, nessa ordem: primeiro o que é mais grave em natureza,
 * depois o que move mais dinheiro. É uma escolha explícita e aparece na tela,
 * para o leitor poder discordar dela sabendo qual é.
 *
 * @param regra      identificador curto da regra que gerou o alerta
 * @param gravidade  1 = curioso, 2 = atípico, 3 = indício forte
 * @param titulo     uma linha, para o cartão do site
 * @param detalhe    a explicação em português, com os números que sustentam o alerta
 * @param centavos   valor envolvido (a soma, quando o alerta junta várias notas)
 * @param envolvidas as despesas que compõem o caso, da maior para a menor
 */
public record Alerta(String regra,
                     int gravidade,
                     String titulo,
                     String detalhe,
                     long centavos,
                     List<Despesa> envolvidas) implements Comparable<Alerta> {

    public Alerta {
        if (gravidade < 1 || gravidade > 3) {
            throw new IllegalArgumentException("Gravidade fora da escala 1..3: " + gravidade);
        }
        if (envolvidas == null || envolvidas.isEmpty()) {
            throw new IllegalArgumentException("Alerta sem despesa não tem o que mostrar.");
        }
        envolvidas = List.copyOf(envolvidas);
    }

    /** A despesa que melhor representa o caso (a de maior valor). */
    public Despesa principal() {
        return envolvidas.get(0);
    }

    public int quantidade() {
        return envolvidas.size();
    }

    @Override
    public int compareTo(Alerta outro) {
        int porGravidade = Integer.compare(outro.gravidade, gravidade);
        if (porGravidade != 0) {
            return porGravidade;
        }
        int porValor = Long.compare(outro.centavos, centavos);
        if (porValor != 0) {
            return porValor;
        }
        return principal().deputado().compareTo(outro.principal().deputado());
    }
}
