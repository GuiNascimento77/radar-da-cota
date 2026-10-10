package dev.radardacota;

import dev.radardacota.dominio.Despesa;
import java.time.LocalDate;

/** Atalhos para montar despesas nos testes, sem repetir doze argumentos. */
public final class Testes {

    public static final LocalDate DIA = LocalDate.of(2026, 3, 10);

    private Testes() {
    }

    public static Despesa despesa(long deputadoId, String deputado, String cnpj, String categoria,
                                 LocalDate data, long centavos) {
        return new Despesa(deputadoId, deputado, "PT", "SP", "FORNECEDOR " + cnpj, cnpj,
                categoria, "", data, centavos, "1", "https://www.camara.leg.br/nota");
    }

    public static Despesa despesa(long deputadoId, String deputado, String cnpj, String categoria,
                                 long centavos) {
        return despesa(deputadoId, deputado, cnpj, categoria, DIA, centavos);
    }

    public static Despesa despesa(String documento, long centavos) {
        return new Despesa(1L, "Deputado Teste", "PT", "SP", "FORNECEDOR X", "123",
                "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.", "", DIA, centavos, documento,
                "https://www.camara.leg.br/nota");
    }

    /** R$ 1.234,56 em centavos, que é como o valor trafega dentro do programa. */
    public static long reais(double reais) {
        return Math.round(reais * 100);
    }
}
