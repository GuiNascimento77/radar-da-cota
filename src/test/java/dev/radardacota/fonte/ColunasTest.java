package dev.radardacota.fonte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColunasTest {

    private final Colunas colunas = Colunas.doCabecalho(List.of(
            "txNomeParlamentar", "sgUF", "vlrLiquido", "urlDocumento"));

    @Test
    @DisplayName("acha a posição da coluna pelo nome")
    void achaPeloNome() {
        assertEquals(0, colunas.posicao("txNomeParlamentar"));
        assertEquals(2, colunas.posicao("vlrLiquido"));
    }

    @Test
    @DisplayName("lê o valor da coluna pelo nome, sem depender da ordem")
    void lePeloNome() {
        List<String> linha = List.of("Ana", "SP", "1467", "https://camara.leg.br/1");

        assertEquals("Ana", colunas.texto(linha, "txNomeParlamentar"));
        assertEquals("SP", colunas.texto(linha, "sgUF"));
        assertEquals("1467", colunas.texto(linha, "vlrLiquido"));
    }

    @Test
    @DisplayName("aguenta a Câmara inserir uma coluna nova no meio do arquivo")
    void aguentaColunaNova() {
        // Este é o ponto da classe: se a Câmara inserir "sgPartido" na posição 1,
        // tudo que lê por número passaria a ler o campo errado em silêncio. Lendo
        // por nome, nada muda.
        Colunas comColunaNova = Colunas.doCabecalho(List.of(
                "txNomeParlamentar", "sgPartido", "sgUF", "vlrLiquido", "urlDocumento"));

        List<String> linha = List.of("Ana", "PT", "SP", "1467", "https://camara.leg.br/1");
        assertEquals("SP", comColunaNova.texto(linha, "sgUF"));
        assertEquals("1467", comColunaNova.texto(linha, "vlrLiquido"));
    }

    @Test
    @DisplayName("explica o problema quando a coluna que o radar usa não existe mais")
    void colunaQueSumiuExplica() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> colunas.posicao("txtFornecedor"));

        assertTrue(erro.getMessage().contains("txtFornecedor"), erro.getMessage());
        assertTrue(erro.getMessage().contains("vlrLiquido"), "deveria listar o que existe");
    }

    @Test
    @DisplayName("coluna ausente na linha curta vira texto vazio, em vez de estourar")
    void linhaCurtaNaoEstoura() {
        List<String> linhaCurta = List.of("Ana");

        assertEquals("Ana", colunas.texto(linhaCurta, "txNomeParlamentar"));
        assertEquals("", colunas.texto(linhaCurta, "vlrLiquido"));
    }

    @Test
    @DisplayName("tira os espaços em volta do valor")
    void tiraEspacos() {
        assertEquals("Ana", colunas.texto(List.of("  Ana  ", "SP", "1", ""), "txNomeParlamentar"));
    }

    @Test
    @DisplayName("diz se a coluna existe, sem estourar")
    void dizSeExiste() {
        assertTrue(colunas.tem("sgUF"));
        assertFalse(colunas.tem("txtTrecho"));
    }

    @Test
    @DisplayName("ignora espaço em volta do nome da coluna no cabeçalho")
    void ignoraEspacoNoCabecalho() {
        Colunas comEspaco = Colunas.doCabecalho(List.of(" txNomeParlamentar ", "sgUF"));

        assertEquals(0, comEspaco.posicao("txNomeParlamentar"));
    }
}
