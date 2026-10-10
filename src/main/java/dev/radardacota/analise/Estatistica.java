package dev.radardacota.analise;

import java.util.List;

/** As duas contas que as regras precisam, e nada mais. */
public final class Estatistica {

    private Estatistica() {
    }

    /**
     * Percentil pelo método da posição mais próxima, sobre uma lista já ordenada.
     *
     * <p>Percentil, e não média: a distribuição de uma subcota tem uma cauda
     * longa (a maioria das notas de combustível fica na casa das centenas de
     * reais, mas existem notas de milhares). Numa distribuição assim a média é
     * arrastada pelos extremos e o desvio padrão quase não diz nada. O percentil
     * descreve o que é "normal" sem se deixar contaminar pelo que não é.
     */
    public static long percentil(List<Long> ordenada, double p) {
        if (ordenada.isEmpty()) {
            return 0;
        }
        int indice = (int) Math.ceil(p * ordenada.size()) - 1;
        indice = Math.max(0, Math.min(ordenada.size() - 1, indice));
        return ordenada.get(indice);
    }

    /** Maior valor de uma lista, ou zero se estiver vazia. */
    public static long maximo(List<Long> valores) {
        long maior = 0;
        for (long v : valores) {
            if (v > maior) {
                maior = v;
            }
        }
        return maior;
    }
}
