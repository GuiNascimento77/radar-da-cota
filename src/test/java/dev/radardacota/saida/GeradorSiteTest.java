package dev.radardacota.saida;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radardacota.Testes;
import dev.radardacota.analise.Alerta;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A seleção do que entra embutido na página.
 *
 * <p>O ponto destes testes: cortar os primeiros da lista ordenada deixaria as
 * regras mais raras de fora, porque quem dispara muito ocupa todas as vagas.
 */
class GeradorSiteTest {

    private static Alerta alerta(String regra, int gravidade, long centavos) {
        return new Alerta(regra, gravidade, "titulo " + regra, "detalhe " + regra, centavos,
                List.of(Testes.despesa(1L, "Deputado", "123", "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", centavos)));
    }

    /** N alertas de uma regra, do mais caro para o mais barato. */
    private static List<Alerta> muitos(String regra, int gravidade, int quantos, long maiorCentavos) {
        List<Alerta> lista = new ArrayList<>(quantos);
        for (int i = 0; i < quantos; i++) {
            lista.add(alerta(regra, gravidade, maiorCentavos - i));
        }
        return lista;
    }

    @Test
    @DisplayName("lista que cabe no limite passa inteira, sem copia")
    void cabeNoLimite() {
        List<Alerta> alertas = muitos("a", 2, 5, 500);
        assertSame(alertas, GeradorSite.selecionarParaPagina(alertas, 10));
    }

    @Test
    @DisplayName("cada regra leva a mesma cota, mesmo com uma disparando muito mais")
    void cotaIgualPorRegra() {
        List<Alerta> alertas = new ArrayList<>(muitos("muita", 3, 300, 100000));
        alertas.addAll(muitos("pouca", 2, 300, 50000));

        List<Alerta> escolhidos = GeradorSite.selecionarParaPagina(alertas, 100);

        assertEquals(100, escolhidos.size());
        assertEquals(50, escolhidos.stream().filter(a -> a.regra().equals("muita")).count());
        assertEquals(50, escolhidos.stream().filter(a -> a.regra().equals("pouca")).count());
    }

    @Test
    @DisplayName("a vaga que sobra vai para quem tem mais casos, ate o limite")
    void sobraVaiParaQuemTemMais() {
        List<Alerta> alertas = new ArrayList<>(muitos("muita", 3, 300, 100000));
        alertas.addAll(muitos("pouca", 2, 5, 50000));

        List<Alerta> escolhidos = GeradorSite.selecionarParaPagina(alertas, 100);

        assertEquals(100, escolhidos.size());
        assertEquals(95, escolhidos.stream().filter(a -> a.regra().equals("muita")).count());
        assertEquals(5, escolhidos.stream().filter(a -> a.regra().equals("pouca")).count());
    }

    @Test
    @DisplayName("o resultado continua na ordem geral: gravidade e depois valor")
    void mantemAOrdemGeral() {
        List<Alerta> alertas = new ArrayList<>(muitos("muita", 3, 300, 100000));
        alertas.addAll(muitos("pouca", 2, 300, 50000));

        List<Alerta> escolhidos = GeradorSite.selecionarParaPagina(alertas, 100);

        for (int i = 1; i < escolhidos.size(); i++) {
            assertTrue(escolhidos.get(i - 1).compareTo(escolhidos.get(i)) <= 0,
                    "fora de ordem na posição " + i);
        }
        assertEquals(3, escolhidos.get(0).gravidade(), "o mais grave tem que vir primeiro");
    }

    @Test
    @DisplayName("limite menor que o numero de regras ainda pega uma de cada")
    void limiteMinusculo() {
        List<Alerta> alertas = new ArrayList<>();
        alertas.addAll(muitos("a", 3, 10, 900));
        alertas.addAll(muitos("b", 3, 10, 800));
        alertas.addAll(muitos("c", 3, 10, 700));

        List<Alerta> escolhidos = GeradorSite.selecionarParaPagina(alertas, 2);

        assertEquals(2, escolhidos.size());
        assertEquals(2, escolhidos.stream().map(Alerta::regra).distinct().count());
    }
}
