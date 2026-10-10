package dev.radardacota.saida;

import java.util.List;

/**
 * Monta JSON sem biblioteca.
 *
 * <p>Escrever JSON à mão é uma linha de código para cada tipo, e evita arrastar
 * uma dependência inteira para gerar dois arquivos. O que exige cuidado é o
 * escapamento — e isso está em {@link Texto#json(String)}, testado.
 *
 * <p>O uso é por composição: {@code objeto("ano", numero(2026), "nome", texto("Ana"))}.
 * Cada valor já chega pronto em JSON, então listas e objetos aninhados saem
 * naturalmente.
 */
public final class Json {

    private Json() {
    }

    public static String texto(String valor) {
        return Texto.json(valor);
    }

    public static String numero(long valor) {
        return Long.toString(valor);
    }

    public static String decimal(double valor) {
        return String.format(java.util.Locale.ROOT, "%.2f", valor);
    }

    public static String booleano(boolean valor) {
        return Boolean.toString(valor);
    }

    /** Lista a partir de itens já em JSON. */
    public static String lista(List<String> itens) {
        return "[" + String.join(",", itens) + "]";
    }

    /** Objeto a partir de pares "nome", valorEmJson. */
    public static String objeto(String... nomeEValor) {
        if (nomeEValor.length % 2 != 0) {
            throw new IllegalArgumentException("Cada campo precisa de nome e valor.");
        }
        StringBuilder saida = new StringBuilder("{");
        for (int i = 0; i < nomeEValor.length; i += 2) {
            if (i > 0) {
                saida.append(',');
            }
            saida.append(Texto.json(nomeEValor[i])).append(':').append(nomeEValor[i + 1]);
        }
        return saida.append('}').toString();
    }
}
