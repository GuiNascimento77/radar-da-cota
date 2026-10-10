package dev.radardacota.saida;

import java.util.Locale;

/**
 * Escapamento de texto para HTML e JSON.
 *
 * <p>Nome de fornecedor e de deputado vem de fora e vai direto para uma página
 * pública. Sem escapar, um nome com {@code <script>} viraria código rodando no
 * navegador de quem abriu a página. São quatro linhas de código que evitam um
 * problema sério.
 *
 * <p>No JSON há uma armadilha a mais: o dado é embutido dentro de uma tag
 * {@code <script>} na página. O navegador fecha essa tag ao encontrar a
 * sequência {@code </script>} em qualquer lugar do texto — inclusive dentro de
 * uma string JSON. Por isso {@code <}, {@code >} e {@code &} saem como escapes
 * unicode: continuam sendo os mesmos caracteres depois do {@code JSON.parse},
 * mas não têm mais como encerrar a tag antes da hora.
 */
public final class Texto {

    private Texto() {
    }

    public static String html(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder saida = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '&' -> saida.append("&amp;");
                case '<' -> saida.append("&lt;");
                case '>' -> saida.append("&gt;");
                case '"' -> saida.append("&quot;");
                case '\'' -> saida.append("&#39;");
                default -> saida.append(c);
            }
        }
        return saida.toString();
    }

    public static String json(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder saida = new StringBuilder(valor.length() + 16);
        saida.append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                case '<' -> saida.append("\\u003c");
                case '>' -> saida.append("\\u003e");
                case '&' -> saida.append("\\u0026");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.append('"').toString();
    }

    /** Primeira letra maiúscula, resto minúsculo: "COMBUSTÍVEIS E LUBRIFICANTES." vira legível. */
    public static String capitalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String minusculo = valor.toLowerCase(Locale.ROOT).trim();
        return Character.toUpperCase(minusculo.charAt(0)) + minusculo.substring(1);
    }

    /** Corta o texto no limite, terminando em reticências. */
    public static String encurtar(String valor, int limite) {
        if (valor == null) {
            return "";
        }
        String limpo = valor.trim();
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite - 1).trim() + "…";
    }
}
