package dev.radardacota.saida;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoedaTest {

    @Test
    @DisplayName("formata no padrão brasileiro, com ponto de milhar e vírgula decimal")
    void formataPadraoBrasileiro() {
        assertEquals("R$ 1.467,50", Moeda.formatar(146750));
        assertEquals("R$ 0,00", Moeda.formatar(0));
        assertEquals("R$ 0,07", Moeda.formatar(7));
        assertEquals("R$ 1.000.000,00", Moeda.formatar(100_000_000L));
        assertEquals("R$ 137.376.640,79", Moeda.formatar(13_737_664_079L));
    }

    @Test
    @DisplayName("nunca usa espaço inquebrável entre o R$ e o número")
    void semEspacoInquebravel() {
        // O NumberFormat do locale pt-BR separa "R$" do valor com U+00A0. O
        // caractere parece um espaço normal e quebra em qualquer lugar que não o
        // espere — já custou um bug no projeto irmão deste.
        for (long centavos : new long[]{0, 7, 146750, 13_737_664_079L, -146750}) {
            String texto = Moeda.formatar(centavos);
            assertFalse(texto.contains("\u00A0"), "apareceu espaço inquebrável em " + texto);
            assertTrue(texto.startsWith("R$ "), texto);
        }
    }

    @Test
    @DisplayName("lê o formato do portal da Câmara, com ponto decimal e sem milhar")
    void leFormatoDoPortal() {
        // É assim que o arquivo da Câmara escreve: "1467" é mil quatrocentos e
        // sessenta e sete reais, e "114.74" é cento e quatorze reais e setenta e
        // quatro centavos.
        assertEquals(146700, Moeda.paraCentavos("1467"));
        assertEquals(11474, Moeda.paraCentavos("114.74"));
        assertEquals(14_950_000L, Moeda.paraCentavos("149500"));
        assertEquals(99_992_10L, Moeda.paraCentavos("99992.10"));
    }

    @Test
    @DisplayName("lê também os formatos com separador de milhar")
    void leFormatosComMilhar() {
        assertEquals(146750, Moeda.paraCentavos("1.467,50"));
        assertEquals(146750, Moeda.paraCentavos("1,467.50"));
        assertEquals(100_000_00L, Moeda.paraCentavos("100.000,00"));
        assertEquals(146750, Moeda.paraCentavos("1467,5"));
        assertEquals(146750, Moeda.paraCentavos("R$ 1.467,50"));
    }

    @Test
    @DisplayName("trata vazio, nulo e traço como zero")
    void vazioEhZero() {
        assertEquals(0, Moeda.paraCentavos(null));
        assertEquals(0, Moeda.paraCentavos(""));
        assertEquals(0, Moeda.paraCentavos("   "));
        assertEquals(0, Moeda.paraCentavos("-"));
    }

    @Test
    @DisplayName("avisa em vez de adivinhar quando o valor não é número")
    void valorInvalidoAvisa() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> Moeda.paraCentavos("cento e quarenta"));
        assertTrue(erro.getMessage().contains("cento e quarenta"));
    }

    @Test
    @DisplayName("soma centavos sem erro de ponto flutuante")
    void somaExata() {
        // Dez mil notas de R$ 0,10 têm que dar exatamente R$ 1.000,00. Com
        // double, a soma acumula erro e o total do site sairia errado por centavos.
        long umCentavo = Moeda.paraCentavos("0.1");
        long total = 0;
        for (int i = 0; i < 10_000; i++) {
            total += umCentavo;
        }
        assertEquals(100_000L, total);
        assertEquals("R$ 1.000,00", Moeda.formatar(total));
    }

    @Test
    @DisplayName("resume valores grandes para os cartões do topo")
    void resume() {
        assertEquals("R$ 137,4 mi", Moeda.resumir(13_737_664_079L));
        assertEquals("R$ 149,5 mil", Moeda.resumir(14_950_000L));
        assertEquals("R$ 942,00", Moeda.resumir(94_200));
    }
}
