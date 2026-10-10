package dev.radardacota.fonte;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduz nome de coluna em posicao na linha.
 *
 * <p>Ler por nome, e nao por numero fixo, e o que impede o projeto de quebrar
 * quando a Camara inserir uma coluna nova no meio do arquivo — coisa que ja
 * aconteceu outras vezes nessa base.
 */
public final class Colunas {

    private final Map<String, Integer> posicoes = new HashMap<>();

    private Colunas(List<String> cabecalho) {
        for (int i = 0; i < cabecalho.size(); i++) {
            posicoes.put(cabecalho.get(i).trim(), i);
        }
    }

    public static Colunas doCabecalho(List<String> cabecalho) {
        return new Colunas(cabecalho);
    }

    /** Posicao da coluna; explode com mensagem clara se o arquivo mudou de formato. */
    public int posicao(String nome) {
        Integer p = posicoes.get(nome);
        if (p == null) {
            throw new IllegalStateException(
                    "A coluna \"" + nome + "\" nao existe no arquivo da Camara. "
                            + "O formato mudou e o leitor precisa ser atualizado. "
                            + "Colunas encontradas: " + posicoes.keySet());
        }
        return p;
    }

    /** Valor da coluna, ou vazio quando a coluna nao existe ou a linha e curta. */
    public String texto(List<String> linha, String nome) {
        Integer p = posicoes.get(nome);
        if (p == null || p >= linha.size()) {
            return "";
        }
        return linha.get(p).trim();
    }

    public boolean tem(String nome) {
        return posicoes.containsKey(nome);
    }
}
