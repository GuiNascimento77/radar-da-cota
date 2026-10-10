package dev.radardacota.saida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Formata e lê dinheiro em centavos, no padrão brasileiro.
 *
 * <p>Sem {@code NumberFormat}: o formatador do locale {@code pt-BR} separa o
 * "R$" do número com um <b>espaço inquebrável</b> (U+00A0), que parece um espaço
 * normal no editor e quebra em qualquer lugar que não espere esse caractere.
 * Já custou um bug no projeto irmão deste. Aqui a formatação é feita na mão, com
 * espaço comum, e o teste {@code MoedaTest} trava isso.
 *
 * <p>A leitura também não passa por {@code double}. O arquivo da Câmara tem
 * centenas de milhares de valores e eles vão virar soma de milhões de reais:
 * acumular erro de ponto flutuante nessa escala daria manchete errada. Por isso
 * tudo é {@link BigDecimal} com arredondamento explícito.
 */
public final class Moeda {

    private Moeda() {
    }

    /**
     * Lê um valor monetário e devolve centavos, exato.
     *
     * <p>O portal da Câmara escreve o valor com <b>ponto como separador
     * decimal</b> e sem separador de milhar: {@code "1467"} é mil quatrocentos e
     * sessenta e sete reais, e {@code "114.74"} é cento e quatorze reais e
     * setenta e quatro centavos. Vírgula também é aceita, para o caso de o
     * formato mudar. Quando os dois aparecem, o <b>último</b> é o decimal —
     * que é a convenção de quem escreve "1.467,50".
     */
    public static long paraCentavos(String bruto) {
        if (bruto == null) {
            return 0;
        }
        String limpo = bruto.replace("\u00A0", "").replace(" ", "").trim();
        if (limpo.regionMatches(true, 0, "R$", 0, 2)) {
            limpo = limpo.substring(2);   // aceita valor copiado da tela, com o símbolo junto
        }
        if (limpo.isEmpty() || "-".equals(limpo)) {
            return 0;
        }

        int virgula = limpo.lastIndexOf(',');
        int ponto = limpo.lastIndexOf('.');

        String normalizado;
        if (virgula >= 0 && ponto >= 0) {
            normalizado = virgula > ponto
                    ? limpo.replace(".", "").replace(',', '.')
                    : limpo.replace(",", "");
        } else if (virgula >= 0) {
            normalizado = limpo.replace(',', '.');
        } else {
            normalizado = limpo;
        }

        try {
            return new BigDecimal(normalizado)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Não entendi o valor \"" + bruto + "\".", e);
        }
    }

    /** 146750 vira "R$ 1.467,50". */
    public static String formatar(long centavos) {
        long absoluto = Math.abs(centavos);
        String inteiros = String.valueOf(absoluto / 100);
        String decimais = String.format(Locale.ROOT, "%02d", absoluto % 100);

        StringBuilder comMilhar = new StringBuilder();
        for (int i = 0; i < inteiros.length(); i++) {
            if (i > 0 && (inteiros.length() - i) % 3 == 0) {
                comMilhar.append('.');
            }
            comMilhar.append(inteiros.charAt(i));
        }

        return "R$ " + (centavos < 0 ? "-" : "") + comMilhar + "," + decimais;
    }

    /** Versão curta para cabeçalhos: "R$ 137,4 mi" e "R$ 149,5 mil". */
    public static String resumir(long centavos) {
        if (centavos >= 1_000_000_00L) {
            return "R$ " + umaCasa(centavos / 1_000_000_00.0) + " mi";
        }
        if (centavos >= 1_000_00L) {
            return "R$ " + umaCasa(centavos / 1_000_00.0) + " mil";
        }
        return formatar(centavos);
    }

    /** Uma casa decimal, com vírgula — e não o ponto que o Locale.ROOT produziria. */
    private static String umaCasa(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor).replace('.', ',');
    }
}
