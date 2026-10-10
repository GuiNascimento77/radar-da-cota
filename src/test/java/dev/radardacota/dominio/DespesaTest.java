package dev.radardacota.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.radardacota.Testes;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DespesaTest {

    @Test
    @DisplayName("recusa valor negativo")
    void recusaValorNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> Testes.despesa(1, "Ana", "111", "X", LocalDate.of(2026, 1, 1), -1));
    }

    @Test
    @DisplayName("recusa despesa sem data")
    void recusaSemData() {
        assertThrows(IllegalArgumentException.class,
                () -> Testes.despesa(1, "Ana", "111", "X", null, 100));
    }

    @Test
    @DisplayName("aceita valor zero, que é o limite de baixo")
    void aceitaZero() {
        assertEquals(0, Testes.despesa(1, "Ana", "111", "X", LocalDate.of(2026, 1, 1), 0).centavos());
    }

    @Test
    @DisplayName("a chave de fracionamento junta parlamentar, fornecedor e dia")
    void chaveDeFracionamento() {
        Despesa despesa = Testes.despesa(7, "Ana", "111", "X", LocalDate.of(2026, 3, 10), 100);

        assertEquals("7|111|2026-03-10", despesa.chaveDeputadoFornecedorDia());
    }

    @Test
    @DisplayName("mesAno sai no formato que ordena junto")
    void mesAno() {
        assertEquals("2026-03", Testes.despesa(1, "Ana", "1", "X", LocalDate.of(2026, 3, 10), 1).mesAno());
        assertEquals("2026-11", Testes.despesa(1, "Ana", "1", "X", LocalDate.of(2026, 11, 1), 1).mesAno());
    }

    @Test
    @DisplayName("somar devolve uma despesa nova, sem mexer na original")
    void somarNaoAlteraAOriginal() {
        Despesa original = Testes.despesa(1, "Ana", "111", "X", LocalDate.of(2026, 3, 10), 100);

        Despesa somada = original.somar(50);

        assertEquals(100, original.centavos());
        assertEquals(150, somada.centavos());
        assertEquals(original.chaveDeputadoFornecedorDia(), somada.chaveDeputadoFornecedorDia());
    }
}
