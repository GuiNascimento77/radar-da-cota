package dev.radardacota.fonte;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Leitor de CSV por caractere, escrito a mao.
 *
 * <p>Por que nao usar {@code split(";")}: os campos do arquivo da Camara vem
 * entre aspas e podem conter o proprio separador dentro (nome de fornecedor com
 * ";" acontece). Um split ingenuo quebraria a linha no lugar errado e a despesa
 * sairia com o valor de outra coluna. Pior: as aspas tambem podem envolver
 * quebras de linha, entao ler "linha por linha" com {@code readLine} tambem nao
 * serve. Este leitor trata aspas, separador e quebra de linha de uma vez, e
 * entrega uma linha por vez para quem chamou — sem carregar 43 MB de texto na
 * memoria.
 */
public final class LeitorCsv {

    private final char separador;

    public LeitorCsv(char separador) {
        this.separador = separador;
    }

    /**
     * Le o fluxo inteiro e chama {@code aoLerLinha} a cada linha completa.
     * A primeira linha (cabecalho) tambem e entregue: quem chama decide o que fazer.
     */
    public void ler(Reader entrada, Consumer<List<String>> aoLerLinha) throws IOException {
        BufferedReader origem = entrada instanceof BufferedReader b ? b : new BufferedReader(entrada, 1 << 16);

        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroDeAspas = false;
        boolean comecou = false;
        int lido;

        while ((lido = origem.read()) != -1) {
            char atual = (char) lido;

            // O arquivo comeca com BOM (U+FEFF). Sem descartar, o nome da
            // primeira coluna vira "\uFEFFtxNomeParlamentar" e nada casa.
            if (!comecou) {
                comecou = true;
                if (atual == '\uFEFF') {
                    continue;
                }
            }

            if (dentroDeAspas) {
                if (atual == '"') {
                    origem.mark(1);
                    int proximo = origem.read();
                    if (proximo == '"') {
                        campo.append('"');          // "" dentro de aspas e uma aspa literal
                    } else {
                        dentroDeAspas = false;
                        if (proximo != -1) {
                            origem.reset();
                        }
                    }
                } else {
                    campo.append(atual);
                }
                continue;
            }

            if (atual == '"') {
                dentroDeAspas = true;
            } else if (atual == separador) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else if (atual == '\n') {
                campos.add(campo.toString());
                campo.setLength(0);
                aoLerLinha.accept(List.copyOf(campos));
                campos.clear();
            } else if (atual != '\r') {
                campo.append(atual);
            }
        }

        // Ultima linha sem quebra no fim do arquivo.
        if (campo.length() > 0 || !campos.isEmpty()) {
            campos.add(campo.toString());
            aoLerLinha.accept(List.copyOf(campos));
        }
    }
}
