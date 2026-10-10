package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Roda todas as regras sobre o ano e devolve a lista de alertas já ordenada. */
public final class Analisador {

    private final List<Regra> regras;

    public Analisador() {
        this(List.of(
                new DocumentoRepetido(),
                new FornecedorConcentrado(),
                new ValorAtipico(),
                new Fracionamento()));
    }

    public Analisador(List<Regra> regras) {
        this.regras = List.copyOf(regras);
    }

    public List<Regra> regras() {
        return regras;
    }

    /** Todos os alertas, do mais grave e mais caro para o menos. */
    public List<Alerta> analisar(List<Despesa> despesas) {
        List<Alerta> alertas = new ArrayList<>();
        for (Regra regra : regras) {
            alertas.addAll(regra.analisar(despesas));
        }
        Collections.sort(alertas);
        return alertas;
    }

    /** Quantos alertas cada regra produziu — inclusive as que não acharam nada. */
    public Map<String, Integer> contarPorRegra(List<Alerta> alertas) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Regra regra : regras) {
            contagem.put(regra.nome(), 0);
        }
        for (Alerta alerta : alertas) {
            contagem.merge(alerta.regra(), 1, Integer::sum);
        }
        return contagem;
    }
}
