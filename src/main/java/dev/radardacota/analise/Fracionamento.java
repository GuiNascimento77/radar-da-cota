package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import dev.radardacota.saida.Data;
import dev.radardacota.saida.Moeda;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Várias notas ao mesmo fornecedor no mesmo dia.
 *
 * <p>O sinal aqui é o fracionamento: em vez de uma nota de R$ 12.000, quatro de
 * R$ 3.000 na mesma data, para a mesma empresa. Isso é como se contorna um teto
 * por item, uma exigência de cotação ou um limite de autorização — o total passa
 * do que passaria se fosse lançado de uma vez.
 *
 * <p>Nem sempre é irregular: uma hospedagem cobra por diária, uma passagem aérea
 * por trecho, um posto registra cada abastecimento. Por isso o alerta não é "tem
 * duas notas", e sim "tem duas notas <b>e</b> elas somam um valor alto". O corte
 * é o maior entre R$ 5.000 e três vezes o percentil 99 da subcota — ou seja, o
 * dia inteiro precisa ser grande até para os padrões daquela categoria.
 *
 * <p>A chave é {@code deputado + CNPJ + dia}, a mesma de
 * {@link Despesa#chaveDeputadoFornecedorDia()}.
 */
public final class Fracionamento implements Regra {

    /** R$ 5.000 — piso do dia inteiro. */
    public static final long PISO = 500_000L;

    /** O dia precisa somar ao menos tantas vezes o percentil 99 da subcota. */
    public static final double MULTIPLO_DO_P99 = 3.0;

    @Override
    public String nome() {
        return "fracionamento";
    }

    @Override
    public String titulo() {
        return "Fracionamento";
    }

    @Override
    public String pergunta() {
        return "O mesmo fornecedor recebeu várias notas no mesmo dia, somando alto?";
    }

    @Override
    public String criterio() {
        return "Duas ou mais notas do mesmo parlamentar para o mesmo CNPJ na mesma data, "
                + "somando no mínimo " + Moeda.formatar(PISO) + " — e também no mínimo "
                + MULTIPLO_DO_P99 + " vezes o percentil 99 da subcota, o que for maior.";
    }

    @Override
    public List<Alerta> analisar(List<Despesa> despesas) {
        Map<String, List<Long>> valoresPorCategoria = new HashMap<>();
        for (Despesa despesa : despesas) {
            valoresPorCategoria.computeIfAbsent(despesa.categoria(), k -> new ArrayList<>())
                    .add(despesa.centavos());
        }
        Map<String, Long> p99 = new HashMap<>();
        for (Map.Entry<String, List<Long>> entrada : valoresPorCategoria.entrySet()) {
            List<Long> valores = entrada.getValue();
            valores.sort(null);
            p99.put(entrada.getKey(), Estatistica.percentil(valores, 0.99));
        }

        Map<String, List<Despesa>> porDia = new HashMap<>();
        for (Despesa despesa : despesas) {
            porDia.computeIfAbsent(despesa.chaveDeputadoFornecedorDia(), k -> new ArrayList<>())
                    .add(despesa);
        }

        List<Alerta> alertas = new ArrayList<>();
        for (List<Despesa> notas : porDia.values()) {
            if (notas.size() < 2) {
                continue;
            }

            long soma = notas.stream().mapToLong(Despesa::centavos).sum();
            // A subcota de referência é a que mais pesou no dia.
            Despesa referencia = notas.stream()
                    .max(Comparator.comparingLong(Despesa::centavos))
                    .orElseThrow();
            long limite = Math.max(PISO, (long) (MULTIPLO_DO_P99 * p99.getOrDefault(referencia.categoria(), 0L)));
            if (soma < limite) {
                continue;
            }

            notas.sort(Comparator.comparingLong(Despesa::centavos).reversed());

            String titulo = notas.size() + " notas ao mesmo fornecedor em "
                    + Data.formatar(referencia.data());
            String detalhe = referencia.deputado() + " (" + referencia.partido() + "-"
                    + referencia.uf() + ") pagou " + Moeda.formatar(soma) + " a "
                    + referencia.fornecedor() + " em " + notas.size() + " notas no dia "
                    + Data.formatar(referencia.data()) + ", sendo a maior de "
                    + Moeda.formatar(notas.get(0).centavos()) + ". O corte para a subcota "
                    + referencia.categoria().toLowerCase(java.util.Locale.ROOT)
                    + " neste dia era " + Moeda.formatar(limite) + ".";

            alertas.add(new Alerta(nome(), 2, titulo, detalhe, soma, notas));
        }
        return alertas;
    }
}
