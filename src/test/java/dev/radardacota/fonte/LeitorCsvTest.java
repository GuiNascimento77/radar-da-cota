package dev.radardacota.fonte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeitorCsvTest {

    private List<List<String>> ler(String texto) throws IOException {
        List<List<String>> linhas = new ArrayList<>();
        new LeitorCsv(';').ler(new StringReader(texto), linhas::add);
        return linhas;
    }

    @Test
    @DisplayName("lê campos simples separados por ponto e vírgula")
    void camposSimples() throws IOException {
        List<List<String>> linhas = ler("\"a\";\"b\";\"c\"\n\"1\";\"2\";\"3\"\n");

        assertEquals(2, linhas.size());
        assertEquals(List.of("a", "b", "c"), linhas.get(0));
        assertEquals(List.of("1", "2", "3"), linhas.get(1));
    }

    @Test
    @DisplayName("não quebra a linha quando o separador está dentro das aspas")
    void separadorDentroDeAspas() throws IOException {
        // "POSTO A; B LTDA" é um fornecedor só, e não dois campos.
        List<List<String>> linhas = ler("\"nome\";\"valor\"\n\"POSTO A; B LTDA\";\"1467\"\n");

        assertEquals(2, linhas.get(1).size());
        assertEquals("POSTO A; B LTDA", linhas.get(1).get(0));
        assertEquals("1467", linhas.get(1).get(1));
    }

    @Test
    @DisplayName("não quebra a linha quando há quebra de linha dentro das aspas")
    void quebraDeLinhaDentroDeAspas() throws IOException {
        List<List<String>> linhas = ler("\"nome\";\"obs\"\n\"Fulano\";\"primeira\nsegunda\"\n\"Beltrano\";\"x\"\n");

        assertEquals(3, linhas.size());
        assertEquals("primeira\nsegunda", linhas.get(1).get(1));
        assertEquals("Beltrano", linhas.get(2).get(0));
    }

    @Test
    @DisplayName("aspas duplas dentro de aspas viram uma aspa literal")
    void aspasEscapadas() throws IOException {
        List<List<String>> linhas = ler("\"nome\"\n\"POSTO \"\"CENTRAL\"\" LTDA\"\n");

        assertEquals("POSTO \"CENTRAL\" LTDA", linhas.get(1).get(0));
    }

    @Test
    @DisplayName("descarta o BOM do começo do arquivo")
    void descartaBom() throws IOException {
        // O arquivo da Câmara começa com BOM (U+FEFF). Sem descartar, a primeira
        // coluna se chamaria "(BOM)txNomeParlamentar" e nenhuma busca casaria.
        // O caractere entra por código, e não colado no meio do texto, porque um
        // BOM no meio de um arquivo-fonte é justamente o que ninguém enxerga.
        String comBom = (char) 0xFEFF + "\"txNomeParlamentar\";\"cpf\"\n\"Ana\";\"1\"\n";
        List<List<String>> linhas = ler(comBom);

        assertEquals("txNomeParlamentar", linhas.get(0).get(0));
    }

    @Test
    @DisplayName("entrega a última linha mesmo sem quebra no fim do arquivo")
    void ultimaLinhaSemQuebra() throws IOException {
        List<List<String>> linhas = ler("\"a\";\"b\"\n\"1\";\"2\"");

        assertEquals(2, linhas.size());
        assertEquals(List.of("1", "2"), linhas.get(1));
    }

    @Test
    @DisplayName("ignora o CR do fim de linha do Windows")
    void ignoraCr() throws IOException {
        List<List<String>> linhas = ler("\"a\";\"b\"\r\n\"1\";\"2\"\r\n");

        assertEquals(List.of("1", "2"), linhas.get(1));
    }

    @Test
    @DisplayName("campo vazio no meio da linha vira string vazia, e não some")
    void campoVazioPreservado() throws IOException {
        List<List<String>> linhas = ler("\"a\";\"b\";\"c\"\n\"1\";;\"3\"\n");

        assertEquals(3, linhas.get(1).size());
        assertEquals("", linhas.get(1).get(1));
    }

    @Test
    @DisplayName("campo entre aspas que nunca fecha não perde o que veio depois")
    void aspasNuncaFechadas() throws IOException {
        // Arquivo truncado no meio de um campo. Não dá para adivinhar onde ele
        // terminaria, então o leitor entrega o resto do arquivo como um campo só —
        // o que é melhor do que descartar a linha inteira em silêncio.
        List<List<String>> linhas = ler("\"nome\";\"valor\"\n\"sem fim;1467");

        assertEquals(2, linhas.size());
        assertEquals("sem fim;1467", linhas.get(1).get(0));
    }

    @Test
    @DisplayName("não engole o caractere seguinte a um campo entre aspas")
    void naoEngoleProximoCampo() throws IOException {
        // Regressão: a leitura da aspa de fechamento usa mark/reset e, se o reset
        // escapar, o próximo caractere desaparece silenciosamente.
        List<List<String>> linhas = ler("\"a\";\"b\";\"c\"\n");

        assertEquals(List.of("a", "b", "c"), linhas.get(0));
    }

    @Test
    @DisplayName("propaga erro de leitura de quem forneceu o fluxo")
    void propagaErroDoFluxo() {
        java.io.Reader quebrado = new java.io.Reader() {
            @Override
            public int read(char[] destino, int inicio, int tamanho) throws IOException {
                throw new IOException("conexão caiu no meio do arquivo");
            }

            @Override
            public int read() throws IOException {
                throw new IOException("conexão caiu no meio do arquivo");
            }

            @Override
            public void close() {
            }
        };

        IOException erro = assertThrows(IOException.class,
                () -> new LeitorCsv(';').ler(quebrado, linha -> { }));
        assertTrue(erro.getMessage().contains("conexão caiu"));
    }
}
