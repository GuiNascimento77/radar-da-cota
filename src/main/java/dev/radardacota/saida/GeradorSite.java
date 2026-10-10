package dev.radardacota.saida;

import dev.radardacota.analise.Alerta;
import dev.radardacota.analise.Regra;
import dev.radardacota.analise.Resumo;
import dev.radardacota.dominio.Despesa;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escreve a página e os dois arquivos de dados.
 *
 * <p>A página é um HTML só, sem CDN, sem build de front-end e sem requisição
 * nenhuma para carregar: o CSS e o JavaScript vão embutidos e os dados vão
 * dentro de uma tag {@code <script type="application/json">}. Isso significa
 * que o arquivo funciona aberto direto do disco, num servidor de arquivos
 * estáticos, no GitHub Pages ou dentro de um e-mail — e continua funcionando
 * quando alguma CDN sai do ar.
 *
 * <p>Os mesmos dados também saem em {@code dados.json} e {@code alertas.json},
 * para quem quiser consumir em outro lugar sem raspar HTML.
 *
 * <p>O modelo da página fica em {@code src/main/resources/site/modelo.html} e
 * vem com marcadores ({@code {{ANO}}}, {@code {{DADOS}}}...). Montar o HTML
 * dentro de Java deixaria o CSS e o JavaScript presos em strings com escape
 * duplo — ilegível para quem for mexer no visual depois.
 */
public final class GeradorSite {

    public static final String REPOSITORIO = "https://github.com/GuiNascimento77/radar-da-cota";

    /** Quantos alertas, no máximo, entram embutidos na página. O resto fica no JSON. */
    public static final int MAXIMO_DE_ALERTAS_NA_PAGINA = 400;

    /** Quantos documentos listar dentro de um alerta, para o JSON não explodir. */
    private static final int NOTAS_POR_ALERTA = 12;

    private final int maximoDeAlertasNaPagina;

    public GeradorSite() {
        this(MAXIMO_DE_ALERTAS_NA_PAGINA);
    }

    public GeradorSite(int maximoDeAlertasNaPagina) {
        this.maximoDeAlertasNaPagina = maximoDeAlertasNaPagina;
    }

    /**
     * @param destino  pasta onde os arquivos são gravados (o {@code site/} do projeto)
     * @param resumo   os totais do ano
     * @param alertas  todos os alertas, já ordenados
     * @param regras   as regras que rodaram, na ordem de registro
     */
    public void gerar(Path destino, Resumo resumo, List<Alerta> alertas,
                      List<Regra> regras, Map<String, Integer> contagemPorRegra) throws IOException {
        Files.createDirectories(destino);

        Files.writeString(destino.resolve("dados.json"),
                jsonDoResumo(resumo, contagemPorRegra), StandardCharsets.UTF_8);
        Files.writeString(destino.resolve("alertas.json"),
                jsonDosAlertas(resumo, alertas, alertas.size(), regras), StandardCharsets.UTF_8);
        Files.writeString(destino.resolve("index.html"),
                pagina(resumo, alertas, regras, contagemPorRegra), StandardCharsets.UTF_8);
    }

    private String pagina(Resumo resumo, List<Alerta> alertas, List<Regra> regras,
                          Map<String, Integer> contagemPorRegra) throws IOException {
        List<Alerta> embutidos = selecionarParaPagina(alertas, maximoDeAlertasNaPagina);

        return modelo()
                .replace("{{ANO}}", String.valueOf(resumo.ano()))
                .replace("{{NOTAS}}", String.format("%,d", resumo.totalNotas()).replace(',', '.'))
                .replace("{{GERADO_EM}}", Data.formatar(resumo.geradoEm()))
                .replace("{{REPOSITORIO}}", REPOSITORIO)
                .replace("{{NUMEROS}}", numeros(resumo, alertas.size()))
                .replace("{{TOTAL_ALERTAS}}", String.format("%,d", alertas.size()).replace(',', '.'))
                .replace("{{REGRAS}}", htmlDasRegras(regras, contagemPorRegra))
                .replace("{{DADOS}}", jsonDoResumo(resumo, contagemPorRegra))
                .replace("{{ALERTAS}}", jsonDosAlertas(resumo, embutidos, alertas.size(), regras));
    }

    /**
     * Escolhe quais alertas entram embutidos na página.
     *
     * <p>Cortar simplesmente os primeiros da lista ordenada não serve: como a
     * ordem é por gravidade e depois por valor, uma regra que dispara muito
     * (concentração de fornecedor, por exemplo) ocupa todas as vagas e as
     * outras aparecem zeradas na página, mesmo tendo centenas de casos no ano.
     *
     * <p>Então a seleção é em duas passadas: primeiro cada regra leva uma cota
     * igual, depois as vagas que sobraram (das regras que não encheram a cota)
     * são preenchidas na ordem geral.
     */
    static List<Alerta> selecionarParaPagina(List<Alerta> alertas, int maximo) {
        if (alertas.size() <= maximo) {
            return alertas;
        }
        int quantasRegras = 0;
        Map<String, Integer> quantasPorRegra = new HashMap<>();
        for (Alerta alerta : alertas) {
            if (quantasPorRegra.putIfAbsent(alerta.regra(), 0) == null) {
                quantasRegras++;
            }
        }
        int cota = Math.max(1, maximo / Math.max(1, quantasRegras));

        boolean[] escolhido = new boolean[alertas.size()];
        int total = 0;
        for (int i = 0; i < alertas.size() && total < maximo; i++) {
            String regra = alertas.get(i).regra();
            int jaEscolhidos = quantasPorRegra.get(regra);
            if (jaEscolhidos < cota) {
                quantasPorRegra.put(regra, jaEscolhidos + 1);
                escolhido[i] = true;
                total++;
            }
        }
        for (int i = 0; i < alertas.size() && total < maximo; i++) {
            if (!escolhido[i]) {
                escolhido[i] = true;
                total++;
            }
        }
        List<Alerta> saida = new ArrayList<>(total);
        for (int i = 0; i < alertas.size(); i++) {
            if (escolhido[i]) {
                saida.add(alertas.get(i));
            }
        }
        return saida;
    }

    private String modelo() throws IOException {
        try (InputStream entrada = GeradorSite.class.getResourceAsStream("/site/modelo.html")) {
            if (entrada == null) {
                throw new IOException("O modelo da página (/site/modelo.html) não está no classpath. "
                        + "Ele deveria vir de src/main/resources/site/modelo.html.");
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /* ------------------------------------------------------------------ dados */

    private String jsonDoResumo(Resumo resumo, Map<String, Integer> contagemPorRegra) {
        List<String> regras = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : contagemPorRegra.entrySet()) {
            regras.add(Json.objeto("regra", Json.texto(entrada.getKey()),
                    "alertas", Json.numero(entrada.getValue())));
        }

        return Json.objeto(
                "ano", Json.numero(resumo.ano()),
                "geradoEm", Json.texto(resumo.geradoEm().toString()),
                "fonte", Json.texto("https://www.camara.leg.br/cotas/Ano-" + resumo.ano() + ".csv.zip"),
                "repositorio", Json.texto(REPOSITORIO),
                "totalCentavos", Json.numero(resumo.totalCentavos()),
                "totalNotas", Json.numero(resumo.totalNotas()),
                "deputados", Json.numero(resumo.deputados()),
                "fornecedores", Json.numero(resumo.fornecedores()),
                "mediaPorNotaCentavos", Json.numero(resumo.mediaPorNota()),
                "mediaPorDeputadoCentavos", Json.numero(resumo.mediaPorDeputado()),
                "alertasPorRegra", Json.lista(regras),
                "porCategoria", fatias(resumo.porCategoria()),
                "porMes", fatias(resumo.porMes()),
                "porUf", fatias(resumo.porUf()),
                "porPartido", fatias(resumo.porPartido()),
                "topDeputados", fatias(resumo.topDeputados()),
                "topFornecedores", fatias(resumo.topFornecedores()));
    }

    private String fatias(List<Resumo.Fatia> fatias) {
        List<String> itens = new ArrayList<>(fatias.size());
        for (Resumo.Fatia fatia : fatias) {
            itens.add(Json.objeto("rotulo", Json.texto(fatia.rotulo()),
                    "centavos", Json.numero(fatia.centavos()),
                    "notas", Json.numero(fatia.notas())));
        }
        return Json.lista(itens);
    }

    private String jsonDosAlertas(Resumo resumo, List<Alerta> alertas, int total, List<Regra> regras) {
        List<String> nomes = new ArrayList<>();
        for (Regra regra : regras) {
            nomes.add(Json.texto(regra.nome()) + ":" + Json.texto(regra.titulo()));
        }

        List<String> itens = new ArrayList<>(alertas.size());
        for (Alerta alerta : alertas) {
            itens.add(jsonDoAlerta(alerta));
        }

        return Json.objeto(
                "ano", Json.numero(resumo.ano()),
                "geradoEm", Json.texto(resumo.geradoEm().toString()),
                "fonte", Json.texto("https://www.camara.leg.br/cotas/Ano-" + resumo.ano() + ".csv.zip"),
                "total", Json.numero(total),
                "embutidos", Json.numero(alertas.size()),
                "regras", "{" + String.join(",", nomes) + "}",
                "alertas", Json.lista(itens));
    }

    private String jsonDoAlerta(Alerta alerta) {
        Despesa principal = alerta.principal();

        List<String> notas = new ArrayList<>();
        for (int i = 0; i < alerta.envolvidas().size() && i < NOTAS_POR_ALERTA; i++) {
            Despesa despesa = alerta.envolvidas().get(i);
            notas.add(Json.objeto(
                    "data", Json.texto(despesa.data().toString()),
                    "centavos", Json.numero(despesa.centavos()),
                    "documento", Json.texto(despesa.documento()),
                    "categoria", Json.texto(despesa.categoria()),
                    "url", Json.texto(despesa.urlDocumento())));
        }

        return Json.objeto(
                "regra", Json.texto(alerta.regra()),
                "gravidade", Json.numero(alerta.gravidade()),
                "titulo", Json.texto(alerta.titulo()),
                "detalhe", Json.texto(alerta.detalhe()),
                "centavos", Json.numero(alerta.centavos()),
                "deputado", Json.texto(principal.deputado()),
                "partido", Json.texto(principal.partido()),
                "uf", Json.texto(principal.uf()),
                "categoria", Json.texto(principal.categoria()),
                "fornecedor", Json.texto(principal.fornecedor()),
                "cnpj", Json.texto(principal.cnpj()),
                "data", Json.texto(principal.data().toString()),
                "quantidade", Json.numero(alerta.quantidade()),
                "notas", Json.lista(notas));
    }

    /* ------------------------------------------------------------------ html */

    private String numeros(Resumo resumo, int totalDeAlertas) {
        return cartaoNumero(Moeda.resumir(resumo.totalCentavos()), "Total pago em " + resumo.ano(), false)
                + cartaoNumero(String.format("%,d", resumo.totalNotas()).replace(',', '.'),
                        "Notas fiscais", false)
                + cartaoNumero(String.format("%,d", resumo.deputados()).replace(',', '.'),
                        "Parlamentares", false)
                + cartaoNumero(String.format("%,d", totalDeAlertas).replace(',', '.'),
                        "Alertas no ano", true)
                + cartaoNumero(Moeda.formatar(resumo.mediaPorNota()), "Média por nota", false);
    }

    private String cartaoNumero(String valor, String rotulo, boolean destaque) {
        return "<div class=\"numero" + (destaque ? " destaque" : "") + "\"><b>" + Texto.html(valor)
                + "</b><span>" + Texto.html(rotulo) + "</span></div>";
    }

    private String htmlDasRegras(List<Regra> regras, Map<String, Integer> contagemPorRegra) {
        StringBuilder html = new StringBuilder();
        int numero = 0;
        for (Regra regra : regras) {
            numero++;
            html.append("<div class=\"caixa\">");
            html.append("<span class=\"numero-regra\">regra ").append(numero).append(" · ")
                    .append(contagemPorRegra.getOrDefault(regra.nome(), 0)).append(" alertas</span>");
            html.append("<h3>").append(Texto.html(regra.titulo())).append("</h3>");
            html.append("<p class=\"pergunta\">").append(Texto.html(regra.pergunta())).append("</p>");
            html.append("<p>").append(Texto.html(regra.criterio())).append("</p>");
            html.append("</div>");
        }
        return html.toString();
    }
}
