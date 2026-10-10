package dev.radardacota.analise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radardacota.Testes;
import dev.radardacota.dominio.Despesa;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Cada regra tem um caso que deve disparar e um que não deve. */
class RegrasTest {

    /**
     * Uma massa de notas iguais, para o percentil ter o que descrever.
     *
     * <p>Todas com o mesmo valor, de propósito: assim nenhuma delas fica acima
     * do próprio percentil, e qualquer alerta que aparecer nos testes veio da
     * despesa que o teste acrescentou depois.
     */
    private static List<Despesa> massa(String categoria, int quantas, long centavos) {
        List<Despesa> despesas = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            despesas.add(Testes.despesa(90000 + i % 10, "Comum " + i % 10,
                    "000" + i % 5, categoria, LocalDate.of(2026, i % 12 + 1, i % 27 + 1), centavos));
        }
        return despesas;
    }

    @Nested
    @DisplayName("valor atípico")
    class ValorAtipicoTeste {

        private static final String CATEGORIA = "COMBUSTÍVEIS E LUBRIFICANTES.";

        @Test
        @DisplayName("acende quando a nota passa do percentil 99,5 da própria subcota")
        void acendeAcimaDoCorte() {
            List<Despesa> despesas = new ArrayList<>(massa(CATEGORIA, 300, 20_000));
            despesas.add(Testes.despesa(1, "Fora do Padrão", "999", CATEGORIA, 900_000));

            List<Alerta> alertas = new ValorAtipico().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals(900_000, alertas.get(0).centavos());
            assertTrue(alertas.get(0).detalhe().contains("Fora do Padrão"));
            assertEquals(2, alertas.get(0).gravidade());
        }

        @Test
        @DisplayName("não acende para nota dentro do padrão da subcota")
        void naoAcendeDentroDoPadrao() {
            List<Despesa> despesas = massa(CATEGORIA, 300, 20_000);

            assertTrue(new ValorAtipico().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("não acende abaixo do piso, mesmo sendo o maior da subcota")
        void respeitaOPiso() {
            // Categoria de valores baixos: o percentil cairia na casa das dezenas
            // de reais e o "atípico" seria irrelevante.
            List<Despesa> despesas = new ArrayList<>(massa("TELEFONIA", 300, 200));
            despesas.add(Testes.despesa(1, "Maior da Subcota", "999", "TELEFONIA", 4_000));

            assertTrue(new ValorAtipico().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("ignora subcota com poucas notas, onde percentil não significa nada")
        void ignoraSubcotaPequena() {
            List<Despesa> despesas = new ArrayList<>(massa("SERVIÇO RARO", 10, 20_000));
            despesas.add(Testes.despesa(1, "Fora do Padrão", "999", "SERVIÇO RARO", 900_000));

            assertTrue(new ValorAtipico().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("compara dentro da subcota, e não entre subcotas")
        void comparaDentroDaSubcota() {
            // R$ 9.000 é normal em divulgação e absurdo em telefonia. A mesma
            // despesa tem que acender numa e não na outra.
            List<Despesa> despesas = new ArrayList<>(massa("TELEFONIA", 300, 200));
            despesas.addAll(massa("DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", 300, 900_000));
            despesas.add(Testes.despesa(1, "Grande na Telefonia", "999", "TELEFONIA", 900_000));

            List<Alerta> alertas = new ValorAtipico().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals("TELEFONIA", alertas.get(0).principal().categoria());
        }
    }

    @Nested
    @DisplayName("fornecedor concentrado")
    class FornecedorConcentradoTeste {

        private static final String CATEGORIA = "MANUTENÇÃO DE ESCRITÓRIO";

        @Test
        @DisplayName("acende quando quase toda a subcota vai para um CNPJ só")
        void acendeComConcentracao() {
            List<Despesa> despesas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                despesas.add(Testes.despesa(1, "Monofornecedor", "555", CATEGORIA,
                        LocalDate.of(2026, i + 1, 5), 600_000));
            }

            List<Alerta> alertas = new FornecedorConcentrado().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals(3_600_000, alertas.get(0).centavos());
            assertEquals(6, alertas.get(0).quantidade());
            assertEquals(3, alertas.get(0).gravidade());
        }

        @Test
        @DisplayName("não acende quando a subcota está dividida entre fornecedores")
        void naoAcendeSemConcentracao() {
            List<Despesa> despesas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                despesas.add(Testes.despesa(1, "Diversificado", "CNPJ" + i, CATEGORIA,
                        LocalDate.of(2026, i + 1, 5), 600_000));
            }

            assertTrue(new FornecedorConcentrado().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("não acende abaixo do piso de R$ 20 mil")
        void respeitaOPiso() {
            List<Despesa> despesas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                despesas.add(Testes.despesa(1, "Pequeno", "555", CATEGORIA,
                        LocalDate.of(2026, i + 1, 5), 100_000));
            }

            assertTrue(new FornecedorConcentrado().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("não acende com uma ou duas notas, que é compra e não dependência")
        void respeitaMinimoDeNotas() {
            List<Despesa> despesas = List.of(
                    Testes.despesa(1, "Compra Grande", "555", CATEGORIA, 5_000_000),
                    Testes.despesa(1, "Compra Grande", "555", CATEGORIA, 5_000_000));

            assertTrue(new FornecedorConcentrado().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("cada parlamentar é comparado com ele mesmo, não com os outros")
        void comparaDentroDoParlamentar() {
            List<Despesa> despesas = new ArrayList<>();
            // O deputado 2 gastou dez vezes mais que o 1, mas pulverizado.
            for (int i = 0; i < 6; i++) {
                despesas.add(Testes.despesa(1, "Concentrado", "555", CATEGORIA,
                        LocalDate.of(2026, i + 1, 5), 600_000));
                despesas.add(Testes.despesa(2, "Pulverizado", "CNPJ" + i, CATEGORIA,
                        LocalDate.of(2026, i + 1, 5), 600_000));
            }

            List<Alerta> alertas = new FornecedorConcentrado().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals("Concentrado", alertas.get(0).principal().deputado());
        }
    }

    @Nested
    @DisplayName("fracionamento")
    class FracionamentoTeste {

        private static final String CATEGORIA = "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.";

        @Test
        @DisplayName("acende quando o mesmo fornecedor recebe várias notas no mesmo dia")
        void acendeComVariasNotasNoDia() {
            List<Despesa> despesas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                despesas.add(Testes.despesa(1, "Fracionado", "555", CATEGORIA,
                        LocalDate.of(2026, 7, 14), 300_000));
            }

            List<Alerta> alertas = new Fracionamento().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals(1_200_000, alertas.get(0).centavos());
            assertEquals(4, alertas.get(0).quantidade());
        }

        @Test
        @DisplayName("não acende com uma nota só, por maior que seja")
        void naoAcendeComUmaNota() {
            List<Despesa> despesas = List.of(
                    Testes.despesa(1, "Uma Nota", "555", CATEGORIA, 5_000_000));

            assertTrue(new Fracionamento().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("não acende quando as notas do dia somam pouco")
        void naoAcendeComValorBaixo() {
            List<Despesa> despesas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                despesas.add(Testes.despesa(1, "Pequeno", "555", CATEGORIA,
                        LocalDate.of(2026, 7, 14), 20_000));
            }

            assertTrue(new Fracionamento().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("exige o mesmo fornecedor e o mesmo dia")
        void exigeMesmoFornecedorEDia() {
            List<Despesa> outroFornecedor = List.of(
                    Testes.despesa(1, "A", "555", CATEGORIA, LocalDate.of(2026, 7, 14), 400_000),
                    Testes.despesa(1, "A", "666", CATEGORIA, LocalDate.of(2026, 7, 14), 400_000));
            List<Despesa> outroDia = List.of(
                    Testes.despesa(1, "B", "555", CATEGORIA, LocalDate.of(2026, 7, 14), 400_000),
                    Testes.despesa(1, "B", "555", CATEGORIA, LocalDate.of(2026, 7, 15), 400_000));

            assertTrue(new Fracionamento().analisar(outroFornecedor).isEmpty());
            assertTrue(new Fracionamento().analisar(outroDia).isEmpty());
        }

        @Test
        @DisplayName("o corte acompanha a subcota: o que é alto em telefonia é baixo em divulgação")
        void corteAcompanhaASubcota() {
            // R$ 12.000 em quatro notas no mesmo dia. Na telefonia isso é enorme;
            // na divulgação, onde o padrão já é de dezenas de milhares, não.
            List<Despesa> telefonia = new ArrayList<>(massa("TELEFONIA", 300, 20_000));
            for (int i = 0; i < 4; i++) {
                telefonia.add(Testes.despesa(1, "Fracionado", "555", "TELEFONIA",
                        LocalDate.of(2026, 7, 14), 300_000));
            }

            List<Despesa> divulgacao = new ArrayList<>(massa(CATEGORIA, 300, 3_000_000));
            for (int i = 0; i < 4; i++) {
                divulgacao.add(Testes.despesa(1, "Fracionado", "555", CATEGORIA,
                        LocalDate.of(2026, 7, 14), 300_000));
            }

            assertEquals(1, new Fracionamento().analisar(telefonia).size());
            assertTrue(new Fracionamento().analisar(divulgacao).isEmpty());
        }
    }

    @Nested
    @DisplayName("documento repetido")
    class DocumentoRepetidoTeste {

        @Test
        @DisplayName("acende quando a mesma nota aparece duas vezes")
        void acendeComNotaDuplicada() {
            List<Despesa> despesas = List.of(
                    Testes.despesa("5150", 840_000),
                    Testes.despesa("5150", 840_000));

            List<Alerta> alertas = new DocumentoRepetido().analisar(despesas);

            assertEquals(1, alertas.size());
            assertEquals(1_680_000, alertas.get(0).centavos());
            assertEquals(3, alertas.get(0).gravidade());
            assertTrue(alertas.get(0).titulo().contains("5150"));
        }

        @Test
        @DisplayName("não acende com números de documento diferentes")
        void naoAcendeComNumerosDiferentes() {
            List<Despesa> despesas = List.of(
                    Testes.despesa("5150", 840_000),
                    Testes.despesa("5151", 840_000));

            assertTrue(new DocumentoRepetido().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("não acende com valores diferentes, mesmo com o número repetido")
        void naoAcendeComValoresDiferentes() {
            // Dois lançamentos sob o mesmo número podem ser duas parcelas de um
            // contrato. O que interessa é o valor idêntico, que indica duplicidade.
            List<Despesa> despesas = List.of(
                    Testes.despesa("5150", 840_000),
                    Testes.despesa("5150", 420_000));

            assertTrue(new DocumentoRepetido().analisar(despesas).isEmpty());
        }

        @Test
        @DisplayName("ignora nota sem número de verdade")
        void ignoraSemNumero() {
            // A base traz "SN", "S/N" e vazio em muitos lançamentos. Comparar
            // esses registros entre si não diz nada sobre duplicidade.
            for (String semNumero : List.of("", "SN", "s/n", "S/N", "0", "-")) {
                List<Despesa> despesas = List.of(
                        Testes.despesa(semNumero, 840_000),
                        Testes.despesa(semNumero, 840_000));

                assertTrue(new DocumentoRepetido().analisar(despesas).isEmpty(),
                        "deveria ignorar o número \"" + semNumero + "\"");
            }
        }

        @Test
        @DisplayName("não acende para notas iguais de parlamentares diferentes")
        void naoAcendeEntreParlamentares() {
            List<Despesa> despesas = List.of(
                    Testes.despesa(1, "Ana", "555", "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", 840_000),
                    Testes.despesa(2, "Bia", "555", "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", 840_000));

            assertTrue(new DocumentoRepetido().analisar(despesas).isEmpty());
        }
    }

    @Nested
    @DisplayName("o alerta")
    class AlertaTeste {

        @Test
        @DisplayName("ordena primeiro por gravidade e depois por valor")
        void ordenaPorGravidadeEValor() {
            Alerta graveBarato = new Alerta("x", 3, "t", "d", 100,
                    List.of(Testes.despesa("1", 100)));
            Alerta leveCaro = new Alerta("y", 2, "t", "d", 900_000,
                    List.of(Testes.despesa("2", 900_000)));
            Alerta graveCaro = new Alerta("z", 3, "t", "d", 500_000,
                    List.of(Testes.despesa("3", 500_000)));

            List<Alerta> alertas = new ArrayList<>(List.of(leveCaro, graveBarato, graveCaro));
            java.util.Collections.sort(alertas);

            assertEquals(List.of(graveCaro, graveBarato, leveCaro), alertas);
        }

        @Test
        @DisplayName("recusa gravidade fora da escala e alerta sem despesa")
        void valida() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new Alerta("x", 7, "t", "d", 1, List.of(Testes.despesa("1", 1))));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new Alerta("x", 2, "t", "d", 1, List.of()));
        }

        @Test
        @DisplayName("não deixa alterar a lista de despesas por fora")
        void listaImutavel() {
            List<Despesa> original = new ArrayList<>(List.of(Testes.despesa("1", 100)));
            Alerta alerta = new Alerta("x", 2, "t", "d", 100, original);
            original.clear();

            assertEquals(1, alerta.quantidade());
            assertFalse(alerta.envolvidas().isEmpty());
        }
    }
}
