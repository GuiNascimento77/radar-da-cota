package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Os totais que dão contexto aos alertas.
 *
 * <p>Um alerta sem denominador não quer dizer nada: R$ 100.000 gastos por um
 * parlamentar só é notícia se ele gastou isso numa subcota, e não no ano
 * inteiro. Por isso o site carrega também o retrato geral — total, quanto cada
 * subcota e cada estado movimentaram, quem mais gastou, quem mais recebeu.
 */
public final class Resumo {

    /** Uma linha de qualquer tabela do painel. */
    public record Fatia(String rotulo, long centavos, int notas) {
    }

    private final int ano;
    private final LocalDate geradoEm;
    private final long totalCentavos;
    private final int totalNotas;
    private final int deputados;
    private final int fornecedores;
    private final List<Fatia> porCategoria;
    private final List<Fatia> porMes;
    private final List<Fatia> porUf;
    private final List<Fatia> porPartido;
    private final List<Fatia> topDeputados;
    private final List<Fatia> topFornecedores;

    private Resumo(int ano, LocalDate geradoEm, long totalCentavos, int totalNotas, int deputados,
                   int fornecedores, List<Fatia> porCategoria, List<Fatia> porMes, List<Fatia> porUf,
                   List<Fatia> porPartido, List<Fatia> topDeputados, List<Fatia> topFornecedores) {
        this.ano = ano;
        this.geradoEm = geradoEm;
        this.totalCentavos = totalCentavos;
        this.totalNotas = totalNotas;
        this.deputados = deputados;
        this.fornecedores = fornecedores;
        this.porCategoria = porCategoria;
        this.porMes = porMes;
        this.porUf = porUf;
        this.porPartido = porPartido;
        this.topDeputados = topDeputados;
        this.topFornecedores = topFornecedores;
    }

    public static Resumo de(List<Despesa> despesas, int ano, LocalDate geradoEm, int quantosNoTopo) {
        Map<String, long[]> porCategoria = new HashMap<>();
        Map<String, long[]> porMes = new HashMap<>();
        Map<String, long[]> porUf = new HashMap<>();
        Map<String, long[]> porPartido = new HashMap<>();
        Map<String, long[]> porDeputado = new HashMap<>();
        Map<String, long[]> porFornecedor = new HashMap<>();
        Map<String, String> rotuloDeputado = new HashMap<>();

        long total = 0;
        for (Despesa despesa : despesas) {
            total += despesa.centavos();
            somar(porCategoria, despesa.categoria(), despesa.centavos());
            if (despesa.data().getYear() == ano) {
                somar(porMes, despesa.mesAno(), despesa.centavos());
            }
            somar(porUf, despesa.uf(), despesa.centavos());
            somar(porPartido, despesa.partido(), despesa.centavos());
            somar(porDeputado, String.valueOf(despesa.deputadoId()), despesa.centavos());
            rotuloDeputado.put(String.valueOf(despesa.deputadoId()),
                    despesa.deputado() + " (" + despesa.partido() + "-" + despesa.uf() + ")");
            somar(porFornecedor, despesa.fornecedor() + "|" + despesa.cnpj(), despesa.centavos());
        }

        return new Resumo(
                ano,
                geradoEm,
                total,
                despesas.size(),
                porDeputado.size(),
                porFornecedor.size(),
                fatiar(porCategoria, Integer.MAX_VALUE),
                ordenarPorRotulo(porMes),
                fatiar(porUf, Integer.MAX_VALUE),
                fatiar(porPartido, Integer.MAX_VALUE),
                fatiar(porDeputado, rotuloDeputado, quantosNoTopo),
                fatiar(porFornecedor, rotuloFornecedor(porFornecedor), quantosNoTopo));
    }

    private static void somar(Map<String, long[]> mapa, String chave, long centavos) {
        long[] atual = mapa.computeIfAbsent(chave, k -> new long[2]);
        atual[0] += centavos;
        atual[1]++;
    }

    private static List<Fatia> fatiar(Map<String, long[]> mapa, int limite) {
        return fatiar(mapa, Map.of(), limite);
    }

    private static List<Fatia> fatiar(Map<String, long[]> mapa, Map<String, String> rotulos, int limite) {
        List<Fatia> fatias = new ArrayList<>();
        for (Map.Entry<String, long[]> entrada : mapa.entrySet()) {
            String rotulo = rotulos.getOrDefault(entrada.getKey(), entrada.getKey());
            fatias.add(new Fatia(rotulo, entrada.getValue()[0], (int) entrada.getValue()[1]));
        }
        fatias.sort(Comparator.comparingLong(Fatia::centavos).reversed()
                .thenComparing(Fatia::rotulo));
        return limite >= fatias.size() ? fatias : new ArrayList<>(fatias.subList(0, limite));
    }

    /** Meses em ordem cronológica, não por valor. */
    private static List<Fatia> ordenarPorRotulo(Map<String, long[]> mapa) {
        List<Fatia> fatias = fatiar(mapa, Integer.MAX_VALUE);
        fatias.sort(Comparator.comparing(Fatia::rotulo));
        return fatias;
    }

    /** "POSTO X LTDA|12345678000199" vira "POSTO X LTDA" — o CNPJ fica só na chave. */
    private static Map<String, String> rotuloFornecedor(Map<String, long[]> mapa) {
        Map<String, String> rotulos = new HashMap<>();
        for (String chave : mapa.keySet()) {
            rotulos.put(chave, chave.substring(0, chave.lastIndexOf('|')));
        }
        return rotulos;
    }

    public int ano() {
        return ano;
    }

    public LocalDate geradoEm() {
        return geradoEm;
    }

    public long totalCentavos() {
        return totalCentavos;
    }

    public int totalNotas() {
        return totalNotas;
    }

    public int deputados() {
        return deputados;
    }

    public int fornecedores() {
        return fornecedores;
    }

    public List<Fatia> porCategoria() {
        return porCategoria;
    }

    public List<Fatia> porMes() {
        return porMes;
    }

    public List<Fatia> porUf() {
        return porUf;
    }

    public List<Fatia> porPartido() {
        return porPartido;
    }

    public List<Fatia> topDeputados() {
        return topDeputados;
    }

    public List<Fatia> topFornecedores() {
        return topFornecedores;
    }

    public long mediaPorNota() {
        return totalNotas == 0 ? 0 : totalCentavos / totalNotas;
    }

    public long mediaPorDeputado() {
        return deputados == 0 ? 0 : totalCentavos / deputados;
    }
}
