package dev.radardacota.analise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radardacota.Testes;
import dev.radardacota.dominio.Despesa;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResumoTest {

    private static final String COMBUSTIVEL = "COMBUSTÍVEIS E LUBRIFICANTES.";
    private static final String DIVULGACAO = "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.";

    private final List<Despesa> despesas = List.of(
            Testes.despesa(1, "Ana", "111", COMBUSTIVEL, LocalDate.of(2026, 1, 10), 100_00),
            Testes.despesa(1, "Ana", "222", COMBUSTIVEL, LocalDate.of(2026, 2, 10), 200_00),
            Testes.despesa(2, "Bia", "111", DIVULGACAO, LocalDate.of(2026, 1, 11), 5_000_00),
            Testes.despesa(3, "Caio", "333", DIVULGACAO, LocalDate.of(2026, 3, 11), 7_000_00));

    private final Resumo resumo = Resumo.de(despesas, 2026, LocalDate.of(2026, 10, 10), 40);

    @Test
    @DisplayName("soma o total e conta notas, parlamentares e fornecedores")
    void contaOsTotais() {
        assertEquals(12_300_00L, resumo.totalCentavos());
        assertEquals(4, resumo.totalNotas());
        assertEquals(3, resumo.deputados());
        assertEquals(3, resumo.fornecedores());
        assertEquals(LocalDate.of(2026, 10, 10), resumo.geradoEm());
    }

    @Test
    @DisplayName("agrupa por subcota, do maior para o menor")
    void agrupaPorSubcota() {
        List<Resumo.Fatia> categorias = resumo.porCategoria();

        assertEquals(2, categorias.size());
        assertEquals(DIVULGACAO, categorias.get(0).rotulo());
        assertEquals(12_000_00L, categorias.get(0).centavos());
        assertEquals(COMBUSTIVEL, categorias.get(1).rotulo());
        assertEquals(300_00L, categorias.get(1).centavos());
    }

    @Test
    @DisplayName("põe os meses em ordem cronológica, e não do maior para o menor")
    void mesesEmOrdem() {
        List<Resumo.Fatia> meses = resumo.porMes();

        assertEquals(List.of("2026-01", "2026-02", "2026-03"),
                meses.stream().map(Resumo.Fatia::rotulo).toList());
    }

    @Test
    @DisplayName("ranqueia os parlamentares pelo total e mostra partido e estado junto")
    void ranqueiaDeputados() {
        List<Resumo.Fatia> deputados = resumo.topDeputados();

        assertEquals(3, deputados.size());
        assertEquals("Caio (PT-SP)", deputados.get(0).rotulo());
        assertEquals(7_000_00L, deputados.get(0).centavos());
        assertEquals("Ana (PT-SP)", deputados.get(2).rotulo());
        assertEquals(300_00L, deputados.get(2).centavos());
    }

    @Test
    @DisplayName("ranqueia os fornecedores pelo total recebido")
    void ranqueiaFornecedores() {
        List<Resumo.Fatia> fornecedores = resumo.topFornecedores();

        assertEquals("FORNECEDOR 333", fornecedores.get(0).rotulo());
        assertEquals(7_000_00L, fornecedores.get(0).centavos());
        // O mesmo CNPJ aparece para dois parlamentares e vira uma linha só.
        assertEquals("FORNECEDOR 111", fornecedores.get(1).rotulo());
        assertEquals(5_100_00L, fornecedores.get(1).centavos());
        assertEquals(2, fornecedores.get(1).notas());
    }

    @Test
    @DisplayName("corta o ranking no tamanho pedido, sem mexer nos totais")
    void cortaORanking() {
        Resumo curto = Resumo.de(despesas, 2026, LocalDate.of(2026, 10, 10), 1);

        assertEquals(1, curto.topDeputados().size());
        assertEquals(3, curto.deputados(), "o total de parlamentares não muda com o corte");
        assertEquals(12_300_00L, curto.totalCentavos());
    }

    @Test
    @DisplayName("calcula as médias que aparecem nos cartões")
    void calculaMedias() {
        assertEquals(12_300_00L / 4, resumo.mediaPorNota());
        assertEquals(12_300_00L / 3, resumo.mediaPorDeputado());
    }

    @Test
    @DisplayName("aguenta lista vazia sem estourar")
    void aguentaListaVazia() {
        Resumo vazio = Resumo.de(List.of(), 2026, LocalDate.of(2026, 10, 10), 40);

        assertEquals(0, vazio.totalCentavos());
        assertEquals(0, vazio.deputados());
        assertEquals(0, vazio.mediaPorNota());
        assertTrue(vazio.porCategoria().isEmpty());
    }

    @Test
    @DisplayName("não mistura meses de outro ano na série mensal")
    void ignoraMesDeOutroAno() {
        List<Despesa> comAnoErrado = List.of(
                Testes.despesa(1, "Ana", "111", COMBUSTIVEL, LocalDate.of(2025, 12, 30), 100_00),
                Testes.despesa(1, "Ana", "111", COMBUSTIVEL, LocalDate.of(2026, 1, 10), 100_00));

        Resumo misto = Resumo.de(comAnoErrado, 2026, LocalDate.of(2026, 10, 10), 40);

        assertEquals(List.of("2026-01"), misto.porMes().stream().map(Resumo.Fatia::rotulo).toList());
        assertEquals(200_00L, misto.totalCentavos(), "o total soma tudo, a série mensal não");
    }
}
