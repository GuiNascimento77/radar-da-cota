package dev.radardacota.fonte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radardacota.dominio.Despesa;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepositorioDespesasTest {

    /** As colunas que o radar usa, na ordem em que o arquivo da Câmara as traz. */
    private static final List<String> COLUNAS = List.of(
            "txNomeParlamentar", "ideCadastro", "sgUF", "sgPartido", "txtDescricao",
            "txtDescricaoEspecificacao", "txtFornecedor", "txtCNPJCPF", "txtNumero",
            "datEmissao", "vlrLiquido", "urlDocumento");

    private static final String CABECALHO =
            COLUNAS.stream().map(c -> "\"" + c + "\"").collect(java.util.stream.Collectors.joining(";")) + "\n";

    /** Monta uma linha com os valores na ordem de {@link #COLUNAS}. */
    private static String linha(String... valores) {
        StringBuilder linha = new StringBuilder();
        for (int i = 0; i < COLUNAS.size(); i++) {
            if (i > 0) {
                linha.append(';');
            }
            linha.append('"').append(i < valores.length ? valores[i] : "").append('"');
        }
        return linha.append('\n').toString();
    }

    /**
     * Uma despesa boa, para acompanhar as linhas ruins nos testes.
     *
     * <p>O leitor recusa um arquivo em que nenhuma despesa sobrou — se a Câmara
     * mudar o formato, é melhor não gerar site do que gerar um site vazio. Por
     * isso todo teste de descarte precisa de uma linha boa junto.
     */
    private static final String VALIDA =
            linha("Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "2026-01-08T00:00:00", "100", "");

    private RepositorioDespesas.Resultado carregar(String... linhas) throws IOException {
        return RepositorioDespesas.carregar(new StringReader(CABECALHO + String.join("", linhas)));
    }

    @Test
    @DisplayName("lê uma linha completa e monta a despesa")
    void leLinhaCompleta() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(linha(
                "Ana Souza", "123", "SP", "PT", "COMBUSTÍVEIS E LUBRIFICANTES.", "Veículos Automotores",
                "POSTO CENTRAL LTDA", "085.324.290/0013-1", "2849", "2026-01-08T00:00:00",
                "1467", "https://camara.leg.br/1"));

        assertEquals(1, resultado.linhasLidas());
        assertEquals(1, resultado.despesas().size());
        assertEquals(0, resultado.descartadas());

        Despesa despesa = resultado.despesas().get(0);
        assertEquals(123L, despesa.deputadoId());
        assertEquals("Ana Souza", despesa.deputado());
        assertEquals("SP", despesa.uf());
        assertEquals("PT", despesa.partido());
        assertEquals("COMBUSTÍVEIS E LUBRIFICANTES.", despesa.categoria());
        assertEquals("Veículos Automotores", despesa.especificacao());
        assertEquals("POSTO CENTRAL LTDA", despesa.fornecedor());
        assertEquals("08532429000131", despesa.cnpj(), "o CNPJ deve ficar só com dígitos");
        assertEquals("2849", despesa.documento());
        assertEquals(LocalDate.of(2026, 1, 8), despesa.data());
        assertEquals(146700, despesa.centavos());
        assertEquals("https://camara.leg.br/1", despesa.urlDocumento());
    }

    @Test
    @DisplayName("lê valor com centavos do jeito que o portal escreve")
    void leValorComCentavos() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "2026-02-03T00:00:00", "114.74", ""));

        assertEquals(11474, resultado.despesas().get(0).centavos());
    }

    @Test
    @DisplayName("descarta linha sem ideCadastro e conta o motivo")
    void descartaSemParlamentar() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(VALIDA, linha(
                "LID.GOV-CD", "", "NA", "", "X", "", "Y", "1", "1", "2026-01-08T00:00:00", "1467", ""));

        assertEquals(1, resultado.despesas().size());
        assertEquals(1, resultado.semParlamentar());
        assertEquals(1, resultado.descartadas());
    }

    @Test
    @DisplayName("descarta linha sem data de emissão e guarda um aviso")
    void descartaSemData() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(VALIDA, linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "", "1467", ""));

        assertEquals(1, resultado.semData());
        assertTrue(resultado.avisos().stream().anyMatch(a -> a.contains("data")));
    }

    @Test
    @DisplayName("descarta data impossível em vez de deixar estourar")
    void descartaDataImpossivel() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(VALIDA, linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "2026-13-45T00:00:00", "1467", ""));

        assertEquals(1, resultado.semData());
        assertEquals(1, resultado.despesas().size());
    }

    @Test
    @DisplayName("descarta despesa de valor zero")
    void descartaValorZero() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(VALIDA, linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "2026-01-08T00:00:00", "0", ""));

        assertEquals(1, resultado.semValor());
        assertEquals(1, resultado.despesas().size());
    }

    @Test
    @DisplayName("descarta valor ilegível em vez de deixar estourar")
    void descartaValorIlegivel() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(VALIDA, linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "2026-01-08T00:00:00", "não sei", ""));

        assertEquals(1, resultado.semValor());
        assertTrue(resultado.avisos().stream().anyMatch(a -> a.contains("não sei")));
    }

    @Test
    @DisplayName("uma linha ruim não impede as outras de entrarem")
    void linhaRuimNaoDerrubaOResto() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(
                linha("Ana", "1", "SP", "PT", "X", "", "Y", "1", "1", "", "100", "")
                        + linha("Bia", "2", "MG", "PL", "X", "", "Y", "2", "2",
                        "2026-03-04T00:00:00", "200", ""));

        assertEquals(2, resultado.linhasLidas());
        assertEquals(1, resultado.despesas().size());
        assertEquals("Bia", resultado.despesas().get(0).deputado());
    }

    @Test
    @DisplayName("o CNPJ continua válido quando vem com pontos e barra")
    void cnpjComPontuacao() throws IOException {
        RepositorioDespesas.Resultado resultado = carregar(linha(
                "Ana", "1", "SP", "PT", "X", "", "Y", "33.333.333/0001-33", "1",
                "2026-01-08T00:00:00", "100", ""));

        assertEquals("33333333000133", resultado.despesas().get(0).cnpj());
    }

    @Test
    @DisplayName("para com erro claro quando o cabeçalho perdeu uma coluna que o radar usa")
    void cabecalhoSemColunaAvisa() {
        String semValor = COLUNAS.stream()
                .filter(c -> !c.equals("vlrLiquido"))
                .map(c -> "\"" + c + "\"")
                .collect(java.util.stream.Collectors.joining(";"))
                + "\n" + VALIDA;

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> RepositorioDespesas.carregar(new StringReader(semValor)));
        assertTrue(erro.getMessage().contains("vlrLiquido"), erro.getMessage());
        assertTrue(erro.getMessage().contains("txtFornecedor"), "deveria listar o que existe");
    }

    @Test
    @DisplayName("recusa arquivo vazio em vez de gerar um site vazio")
    void recusaArquivoVazio() {
        assertThrows(IOException.class, () -> RepositorioDespesas.carregar(new StringReader("")));
    }

    @Test
    @DisplayName("recusa arquivo só com cabeçalho")
    void recusaSoCabecalho() {
        assertThrows(IOException.class,
                () -> RepositorioDespesas.carregar(new StringReader(CABECALHO)));
    }

    @Test
    @DisplayName("tira a pontuação do CNPJ e do CPF")
    void tiraPontuacao() {
        assertEquals("08532429000131", RepositorioDespesas.somenteDigitos("085.324.290/0013-1"));
        assertEquals("02261265794", RepositorioDespesas.somenteDigitos("022.612.657-94"));
        assertEquals("", RepositorioDespesas.somenteDigitos(""));
    }

    @Test
    @DisplayName("a amostra do demo é lida de ponta a ponta")
    void leAmostraDoDemo() throws IOException {
        RepositorioDespesas.Resultado resultado =
                RepositorioDespesas.carregar(new StringReader(AmostraDemo.csv()));

        assertTrue(resultado.despesas().size() > 300, "esperava algumas centenas de despesas");
        assertEquals(1, resultado.semParlamentar(), "a amostra tem uma linha de órgão");
        assertEquals(1, resultado.semData(), "a amostra tem uma linha sem data");
        assertEquals(0, resultado.malformadas());
        assertFalse(resultado.despesas().stream().anyMatch(d -> d.deputado().equals("LID.GOV-CD")));
        assertTrue(resultado.despesas().stream().noneMatch(d -> d.categoria().isBlank()));
        assertTrue(resultado.despesas().stream().noneMatch(d -> d.fornecedor().isBlank()));
        assertTrue(resultado.despesas().stream().noneMatch(d -> d.cnpj().isBlank()));
        assertTrue(resultado.despesas().stream().allMatch(d -> d.centavos() > 0));
    }
}
