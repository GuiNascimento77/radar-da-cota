package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import dev.radardacota.saida.Moeda;
import dev.radardacota.saida.Texto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma subcota inteira indo para um único fornecedor.
 *
 * <p>Não é ilegal e, em vários casos, é o esperado: quem tem o escritório em
 * uma cidade pequena aluga o imóvel de um locador só e abastece sempre no mesmo
 * posto. Mas quando <b>80% ou mais</b> de tudo que o parlamentar gastou numa
 * subcota no ano foi para uma empresa só, isso deixa de ser escolha de
 * conveniência e passa a ser dependência — e dependência é o que sustenta
 * contrato dirigido, sobrepreço e pagamento por serviço não prestado.
 *
 * <p>O piso de R$ 20.000 em três notas ou mais tira o ruído: abaixo disso o
 * "monofornecedor" é simplesmente o tamanho pequeno da despesa.
 *
 * <p>A comparação é sempre dentro da mesma subcota, porque comparar o posto de
 * gasolina com o gráfico não diria nada.
 */
public final class FornecedorConcentrado implements Regra {

    /** R$ 20.000 — abaixo disto a concentração não move dinheiro suficiente. */
    public static final long PISO = 2_000_000L;

    /** Proporção da subcota que precisa estar num único fornecedor. */
    public static final double FATIA = 0.80;

    /** Menos notas que isto e a "concentração" é só uma compra grande. */
    public static final int MINIMO_DE_NOTAS = 3;

    @Override
    public String nome() {
        return "fornecedor-concentrado";
    }

    @Override
    public String titulo() {
        return "Fornecedor concentrado";
    }

    @Override
    public String pergunta() {
        return "Esta subcota foi quase toda para um fornecedor só?";
    }

    @Override
    public String criterio() {
        return "Um único CNPJ recebeu " + (int) (FATIA * 100) + "% ou mais de tudo que o "
                + "parlamentar gastou naquela subcota no ano, somando pelo menos "
                + Moeda.formatar(PISO) + " em " + MINIMO_DE_NOTAS + " notas ou mais.";
    }

    @Override
    public List<Alerta> analisar(List<Despesa> despesas) {
        Map<String, Long> totalDaSubcota = new HashMap<>();
        Map<String, List<Despesa>> porFornecedor = new HashMap<>();
        Map<String, Despesa> exemplo = new HashMap<>();

        for (Despesa despesa : despesas) {
            String subcota = despesa.deputadoId() + "|" + despesa.categoria();
            totalDaSubcota.merge(subcota, despesa.centavos(), Long::sum);
            porFornecedor.computeIfAbsent(subcota + "|" + despesa.cnpj(), k -> new ArrayList<>())
                    .add(despesa);
            exemplo.putIfAbsent(subcota, despesa);
        }

        List<Alerta> alertas = new ArrayList<>();
        for (Map.Entry<String, List<Despesa>> entrada : porFornecedor.entrySet()) {
            List<Despesa> notas = entrada.getValue();
            if (notas.size() < MINIMO_DE_NOTAS) {
                continue;
            }

            long soma = notas.stream().mapToLong(Despesa::centavos).sum();
            if (soma < PISO) {
                continue;
            }

            int corte = entrada.getKey().lastIndexOf('|');
            String subcota = entrada.getKey().substring(0, corte);
            long total = totalDaSubcota.get(subcota);
            double fatia = total == 0 ? 0 : (double) soma / total;
            if (fatia < FATIA) {
                continue;
            }

            Despesa referencia = exemplo.get(subcota);
            String categoria = Texto.capitalizar(referencia.categoria());

            notas.sort(Comparator.comparingLong(Despesa::centavos).reversed());

            String titulo = (int) Math.round(fatia * 100) + "% de " + categoria
                    + " num fornecedor só";
            String detalhe = referencia.deputado() + " (" + referencia.partido() + "-"
                    + referencia.uf() + ") gastou " + Moeda.formatar(total) + " em " + categoria
                    + " e " + Moeda.formatar(soma) + " disso — "
                    + (int) Math.round(fatia * 100) + "% — foi para "
                    + notas.get(0).fornecedor() + ", em " + notas.size()
                    + " notas ao longo do ano.";

            alertas.add(new Alerta(nome(), 3, titulo, detalhe, soma, notas));
        }
        return alertas;
    }
}
