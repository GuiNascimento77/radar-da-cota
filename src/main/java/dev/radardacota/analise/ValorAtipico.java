package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import dev.radardacota.saida.Data;
import dev.radardacota.saida.Moeda;
import dev.radardacota.saida.Texto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma nota muito acima do padrão da própria subcota.
 *
 * <p>O limite sai da distribuição da própria categoria, e não de uma tabela
 * fixa. Assim a regra acompanha o que a Câmara realmente pratica em cada
 * subcota: uma diária de hotel de R$ 3.000 é fora do padrão, enquanto um
 * contrato de divulgação de R$ 3.000 é banal. O corte é o percentil 99,5 — ou
 * seja, a nota precisa estar entre as 5 em cada 1.000 maiores da categoria
 * para acender.
 *
 * <p>O piso de R$ 1.000 existe para não gastar atenção com centavos: numa
 * subcota de valores baixos, o percentil pode cair na casa das dezenas de reais
 * e o "atípico" viraria "irrelevante".
 *
 * <p>Nada aqui é acusação. Nota acima do padrão tem explicação legítima com
 * frequência — frete de material para o Acre, passagem comprada em cima da hora.
 * O que a regra entrega é a lista curta de coisas que merecem uma olhada.
 */
public final class ValorAtipico implements Regra {

    /** Abaixo disto a categoria tem poucas notas e o percentil não significa nada. */
    public static final int MINIMO_DE_NOTAS = 50;

    /** R$ 1.000 — o que for menor que isto não vale a atenção do leitor. */
    public static final long PISO = 100_000L;

    private final double corte;

    public ValorAtipico() {
        this(0.995);
    }

    public ValorAtipico(double corte) {
        this.corte = corte;
    }

    @Override
    public String nome() {
        return "valor-atipico";
    }

    @Override
    public String titulo() {
        return "Valor atípico";
    }

    @Override
    public String pergunta() {
        return "Esta nota é muito maior que as outras notas da mesma subcota?";
    }

    @Override
    public String criterio() {
        return "Valor acima do percentil " + textoDoCorte() + " da própria subcota, "
                + "com piso de " + Moeda.formatar(PISO) + ". "
                + "Subcotas com menos de " + MINIMO_DE_NOTAS + " notas no ano ficam de fora.";
    }

    private String textoDoCorte() {
        return String.format(java.util.Locale.ROOT, "%.1f", corte * 100).replace(".", ",");
    }

    @Override
    public List<Alerta> analisar(List<Despesa> despesas) {
        Map<String, List<Long>> valoresPorCategoria = new HashMap<>();
        for (Despesa despesa : despesas) {
            valoresPorCategoria.computeIfAbsent(despesa.categoria(), k -> new ArrayList<>())
                    .add(despesa.centavos());
        }

        Map<String, Long> limites = new HashMap<>();
        for (Map.Entry<String, List<Long>> entrada : valoresPorCategoria.entrySet()) {
            List<Long> valores = entrada.getValue();
            if (valores.size() < MINIMO_DE_NOTAS) {
                continue;
            }
            valores.sort(null);
            long limite = Estatistica.percentil(valores, corte);
            limites.put(entrada.getKey(), Math.max(limite, PISO));
        }

        List<Alerta> alertas = new ArrayList<>();
        for (Despesa despesa : despesas) {
            Long limite = limites.get(despesa.categoria());
            if (limite == null || despesa.centavos() <= limite) {
                continue;
            }
            int quantasNotas = valoresPorCategoria.get(despesa.categoria()).size();
            String categoria = Texto.capitalizar(despesa.categoria());

            String titulo = Moeda.formatar(despesa.centavos()) + " em " + categoria;
            String detalhe = despesa.deputado() + " (" + despesa.partido() + "-" + despesa.uf()
                    + ") pagou " + Moeda.formatar(despesa.centavos()) + " a "
                    + despesa.fornecedor() + " em " + Data.formatar(despesa.data()) + ". "
                    + "Para essa subcota, 99,5% das " + quantasNotas + " notas de "
                    + despesa.data().getYear() + " ficaram abaixo de "
                    + Moeda.formatar(limite) + " — esta passou " + Moeda.formatar(despesa.centavos() - limite)
                    + " do corte.";

            alertas.add(new Alerta(nome(), 2, titulo, detalhe, despesa.centavos(), List.of(despesa)));
        }
        return alertas;
    }
}
