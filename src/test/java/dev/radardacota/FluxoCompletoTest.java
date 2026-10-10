package dev.radardacota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radardacota.analise.Alerta;
import dev.radardacota.analise.Analisador;
import dev.radardacota.analise.Resumo;
import dev.radardacota.fonte.AmostraDemo;
import dev.radardacota.fonte.RepositorioDespesas;
import dev.radardacota.saida.GeradorSite;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O caminho inteiro, do CSV até a página, sem rede.
 *
 * <p>É o teste que responde "o projeto funciona?" — e o mesmo caminho que o
 * GitHub Actions roda todo dia, com {@code --demo}.
 */
class FluxoCompletoTest {

    private static RepositorioDespesas.Resultado leitura;
    private static List<Alerta> alertas;
    private static Analisador analisador;
    private static Resumo resumo;
    private static Path site;

    @BeforeAll
    static void gerar(@TempDir Path pasta) throws IOException {
        leitura = RepositorioDespesas.carregar(new StringReader(AmostraDemo.csv()));
        analisador = new Analisador();
        alertas = analisador.analisar(leitura.despesas());
        resumo = Resumo.de(leitura.despesas(), 2026, LocalDate.of(2026, 10, 10), 40);
        site = pasta.resolve("site");
        new GeradorSite().gerar(site, resumo, alertas, analisador.regras(),
                analisador.contarPorRegra(alertas));
    }

    @Test
    @DisplayName("cada uma das quatro regras encontra o caso que a amostra preparou")
    void cadaRegraAchaAlgo() {
        var contagem = analisador.contarPorRegra(alertas);

        assertEquals(4, contagem.size());
        contagem.forEach((regra, quantos) ->
                assertTrue(quantos > 0, "a regra " + regra + " não achou nada na amostra"));
    }

    @Test
    @DisplayName("a amostra produz um alerta para cada caso montado à mão")
    void alertasEsperados() {
        assertTrue(temAlerta("valor-atipico", "Fora do Padrão"));
        assertTrue(temAlerta("fornecedor-concentrado", "Monofornecedor"));
        assertTrue(temAlerta("fracionamento", "Fracionado"));
        assertTrue(temAlerta("documento-repetido", "Nota Dupla"));
    }

    private boolean temAlerta(String regra, String trecho) {
        return alertas.stream().anyMatch(a -> a.regra().equals(regra) && a.detalhe().contains(trecho));
    }

    @Test
    @DisplayName("os alertas saem do mais grave e mais caro para o menos")
    void alertasOrdenados() {
        for (int i = 1; i < alertas.size(); i++) {
            Alerta anterior = alertas.get(i - 1);
            Alerta atual = alertas.get(i);
            assertTrue(anterior.gravidade() > atual.gravidade()
                            || (anterior.gravidade() == atual.gravidade()
                            && anterior.centavos() >= atual.centavos()),
                    "fora de ordem: " + anterior.regra() + " antes de " + atual.regra());
        }
    }

    @Test
    @DisplayName("escreve os três arquivos do site")
    void escreveOsTresArquivos() {
        assertTrue(Files.exists(site.resolve("index.html")));
        assertTrue(Files.exists(site.resolve("dados.json")));
        assertTrue(Files.exists(site.resolve("alertas.json")));
    }

    @Test
    @DisplayName("a página sai sem marcador sobrando e com os números do ano")
    void paginaMontada() throws IOException {
        String html = Files.readString(site.resolve("index.html"), StandardCharsets.UTF_8);

        assertFalse(html.contains("{{"), "sobrou marcador sem substituir: "
                + html.substring(Math.max(0, html.indexOf("{{") - 40), Math.min(html.length(), html.indexOf("{{") + 40)));
        assertTrue(html.contains("Radar da Cota"));
        assertTrue(html.contains("2026"));
        assertTrue(html.contains("10/10/2026"));
        assertTrue(html.contains("https://github.com/GuiNascimento77/radar-da-cota"));
        assertTrue(html.contains("Combustíveis"), "os acentos têm que chegar inteiros ao HTML");
    }

    @Test
    @DisplayName("a página não puxa nada de fora: sem CDN, sem requisição")
    void paginaSemDependenciaExterna() throws IOException {
        String html = Files.readString(site.resolve("index.html"), StandardCharsets.UTF_8);

        assertFalse(html.contains("<script src="), "não deveria carregar script externo");
        assertFalse(html.contains("rel=\"stylesheet\""), "não deveria carregar CSS externo");
        assertFalse(html.contains("fetch("), "não deveria buscar dados por requisição");
    }

    @Test
    @DisplayName("o JSON embutido na página é JSON de verdade e traz todos os alertas")
    void jsonEmbutidoValido() throws IOException {
        String html = Files.readString(site.resolve("index.html"), StandardCharsets.UTF_8);

        String dados = bloco(html, "dados");
        String alertasJson = bloco(html, "alertas");

        assertTrue(dados.startsWith("{") && dados.endsWith("}"), "dados não é um objeto JSON");
        assertTrue(alertasJson.startsWith("{") && alertasJson.endsWith("}"), "alertas não é um objeto JSON");
        assertTrue(dados.contains("\"totalCentavos\":"));
        assertTrue(alertasJson.contains("\"alertas\":["));

        // O navegador fecha a tag <script> ao encontrar "</script>" em qualquer
        // lugar do texto. Dentro do JSON, isso não pode existir.
        assertFalse(alertasJson.contains("</"), "o JSON embutido não pode conter </");
        assertFalse(dados.contains("</"), "o JSON embutido não pode conter </");

        assertEquals(contar(alertasJson, "\"regra\":"), alertas.size());
    }

    private String bloco(String html, String identificador) {
        Matcher combinador = Pattern.compile(
                "<script id=\"" + identificador + "\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL).matcher(html);
        assertTrue(combinador.find(), "não achei o bloco " + identificador + " na página");
        return combinador.group(1);
    }

    private int contar(String texto, String trecho) {
        int total = 0;
        int posicao = texto.indexOf(trecho);
        while (posicao >= 0) {
            total++;
            posicao = texto.indexOf(trecho, posicao + trecho.length());
        }
        return total;
    }

    @Test
    @DisplayName("o alertas.json traz a lista completa, e não só o que coube na página")
    void alertasJsonCompleto() throws IOException {
        String json = Files.readString(site.resolve("alertas.json"), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"total\":" + alertas.size()));
        assertTrue(json.contains("\"embutidos\":" + alertas.size()));
        assertEquals(alertas.size(), contar(json, "\"regra\":"));
    }

    @Test
    @DisplayName("o dados.json traz os totais e as séries do painel")
    void dadosJsonCompleto() throws IOException {
        String json = Files.readString(site.resolve("dados.json"), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"totalCentavos\":" + resumo.totalCentavos()));
        assertTrue(json.contains("\"totalNotas\":" + resumo.totalNotas()));
        assertTrue(json.contains("\"deputados\":" + resumo.deputados()));
        assertTrue(json.contains("\"porCategoria\":["));
        assertTrue(json.contains("\"porMes\":["));
        assertTrue(json.contains("\"topDeputados\":["));
        assertTrue(json.contains("\"topFornecedores\":["));
        assertTrue(json.contains("\"alertasPorRegra\":["));
    }

    @Test
    @DisplayName("corta a lista da página sem cortar a dos arquivos")
    void cortaSoAPagina() throws IOException {
        Path pequeno = site.resolveSibling("site-pequeno");
        new GeradorSite(1).gerar(pequeno, resumo, alertas, analisador.regras(),
                analisador.contarPorRegra(alertas));

        String html = Files.readString(pequeno.resolve("index.html"), StandardCharsets.UTF_8);
        String json = Files.readString(pequeno.resolve("alertas.json"), StandardCharsets.UTF_8);

        assertEquals(1, contar(bloco(html, "alertas"), "\"regra\":"));
        assertEquals(alertas.size(), contar(json, "\"regra\":"));
        assertTrue(bloco(html, "alertas").contains("\"embutidos\":1"));
        assertTrue(bloco(html, "alertas").contains("\"total\":" + alertas.size()));
    }

    @Test
    @DisplayName("nenhum texto da página sai com HTML cru do arquivo de origem")
    void nadaDeHtmlCru() throws IOException {
        // Um fornecedor chamado "</script><img src=x>" tem que chegar escapado.
        List<dev.radardacota.dominio.Despesa> comAtaque = List.of(
                new dev.radardacota.dominio.Despesa(1, "Ana", "PT", "SP",
                        "GRAFICA </script><img src=x onerror=alert(1)> LTDA", "123",
                        "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", "", LocalDate.of(2026, 5, 20),
                        900_000, "1", "https://camara.leg.br/1"));

        Path destino = site.resolveSibling("site-ataque");
        Resumo resumoPequeno = Resumo.de(comAtaque, 2026, LocalDate.of(2026, 10, 10), 40);
        List<Alerta> alertasPequenos = new Analisador().analisar(comAtaque);
        new GeradorSite().gerar(destino, resumoPequeno, alertasPequenos, analisador.regras(),
                analisador.contarPorRegra(alertasPequenos));

        String html = Files.readString(destino.resolve("index.html"), StandardCharsets.UTF_8);
        String json = Files.readString(destino.resolve("dados.json"), StandardCharsets.UTF_8);

        assertFalse(html.contains("</script><img"), "o nome do fornecedor vazou como HTML");
        assertFalse(json.contains("</script>"), "o nome do fornecedor vazou como HTML no JSON");
        assertTrue(html.contains("\\u003c/script\\u003e"), "esperava o escape unicode no JSON embutido");
    }

    @Test
    @DisplayName("o leitor descarta as linhas ruins e mantém o resto")
    void descartesDaAmostra() {
        assertEquals(1, leitura.semParlamentar());
        assertEquals(1, leitura.semData());
        assertEquals(0, leitura.malformadas());
        assertEquals(2, leitura.descartadas());
    }
}
