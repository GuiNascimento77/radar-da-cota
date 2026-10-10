package dev.radardacota.saida;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextoTest {

    @Test
    @DisplayName("escapa o que viraria código na página")
    void escapaHtml() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                Texto.html("<script>alert(1)</script>"));
        assertEquals("Ana &amp; Cia", Texto.html("Ana & Cia"));
        assertEquals("&quot;aspas&quot; e &#39;simples&#39;",
                Texto.html("\"aspas\" e 'simples'"));
    }

    @Test
    @DisplayName("nome nulo vira vazio, e não a palavra null na tela")
    void nuloViraVazio() {
        assertEquals("", Texto.html(null));
        assertEquals("null", Texto.json(null));
    }

    @Test
    @DisplayName("mantém os acentos como estão")
    void mantemAcentos() {
        assertEquals("Manutenção de escritório", Texto.html("Manutenção de escritório"));
    }

    @Test
    @DisplayName("no JSON, < e > viram escape unicode")
    void jsonEscapaSinaisDeTag() {
        // O JSON é embutido dentro de uma tag <script> na página. O navegador
        // fecha essa tag ao encontrar "</script>" em qualquer lugar do texto —
        // inclusive dentro de uma string JSON. Escapando < e >, isso não acontece,
        // e depois do JSON.parse o caractere volta a ser o mesmo.
        String json = Texto.json("Gráfica </script><img src=x> LTDA");

        assertFalse(json.contains("<"), json);
        assertFalse(json.contains(">"), json);
        assertTrue(json.contains("\\u003c"), json);
        assertTrue(json.contains("\\u003e"), json);
    }

    @Test
    @DisplayName("escapa aspas, barra invertida e caracteres de controle")
    void jsonEscapaControle() {
        assertEquals("\"diz \\\"oi\\\"\"", Texto.json("diz \"oi\""));
        assertEquals("\"c:\\\\temp\"", Texto.json("c:\\temp"));
        assertEquals("\"linha\\nquebrada\"", Texto.json("linha\nquebrada"));
        assertEquals("\"\\u0000\\u001f\"", Texto.json("\u0000\u001f"));
    }

    @Test
    @DisplayName("capitaliza subcota escrita em maiúsculas")
    void capitaliza() {
        assertEquals("Combustíveis e lubrificantes.", Texto.capitalizar("COMBUSTÍVEIS E LUBRIFICANTES."));
        assertEquals("", Texto.capitalizar("   "));
        assertEquals("", Texto.capitalizar(null));
    }

    @Test
    @DisplayName("encurta sem cortar no meio de um acento")
    void encurta() {
        assertEquals("curto", Texto.encurtar("curto", 20));
        assertEquals("um texto mui…", Texto.encurtar("um texto muito longo", 13));
    }

    @Test
    @DisplayName("monta objeto JSON com os campos na ordem pedida")
    void montaObjeto() {
        assertEquals("{\"ano\":2026,\"nome\":\"Ana\",\"ok\":true}",
                Json.objeto("ano", Json.numero(2026), "nome", Json.texto("Ana"),
                        "ok", Json.booleano(true)));
    }

    @Test
    @DisplayName("recusa objeto com número ímpar de argumentos")
    void recusaObjetoTorto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Json.objeto("ano"));
    }

    @Test
    @DisplayName("monta lista vazia e lista com itens")
    void montaLista() {
        assertEquals("[]", Json.lista(java.util.List.of()));
        assertEquals("[1,2]", Json.lista(java.util.List.of("1", "2")));
    }
}
