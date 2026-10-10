package dev.radardacota.fonte;

import dev.radardacota.dominio.Despesa;
import dev.radardacota.saida.Moeda;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforma o CSV da Câmara em {@link Despesa}.
 *
 * <p>Lê por nome de coluna, e não por posição: a Câmara já mexeu no
 * cabeçalho desse arquivo antes e vai mexer de novo. Se uma coluna que o radar
 * usa sumir, {@link Colunas#posicao(String)} estoura na primeira linha com a
 * lista do que existe — que é bem melhor do que gerar um site com números
 * trocados.
 *
 * <p>Linha problemática isolada não derruba o processamento: ela é contada e
 * descartada, e o total de descartes aparece no relatório do programa. Assim uma
 * linha estranha em 114 mil não impede o site de sair, mas também não passa
 * escondida.
 */
public final class RepositorioDespesas {

    /**
     * @param despesas        o que deu para aproveitar
     * @param linhasLidas     linhas de dados encontradas no arquivo (sem o cabeçalho)
     * @param semParlamentar  linhas sem ideCadastro — despesas de órgãos e lideranças
     * @param semData         linhas sem data de emissão
     * @param semValor        linhas com valor zerado ou ilegível
     * @param malformadas     linhas que não deram para ler
     * @param avisos          exemplos de linhas problemáticas, para diagnóstico
     */
    public record Resultado(List<Despesa> despesas,
                            int linhasLidas,
                            int semParlamentar,
                            int semData,
                            int semValor,
                            int malformadas,
                            List<String> avisos) {

        public int descartadas() {
            return semParlamentar + semData + semValor + malformadas;
        }
    }

    /**
     * Colunas sem as quais o radar não tem o que fazer.
     *
     * <p>São conferidas uma a uma na primeira linha. Se a Câmara renomear ou
     * remover qualquer uma delas, o programa para aqui, com o nome da coluna e a
     * lista do que existe — em vez de gerar um site inteiro com valores zerados
     * ou trocados, que é bem pior do que não gerar site nenhum.
     */
    private static final List<String> OBRIGATORIAS = List.of(
            "txNomeParlamentar", "ideCadastro", "sgUF", "sgPartido", "txtDescricao",
            "txtDescricaoEspecificacao", "txtFornecedor", "txtCNPJCPF", "txtNumero",
            "datEmissao", "vlrLiquido", "urlDocumento");

    private RepositorioDespesas() {
    }

    public static Resultado carregar(Reader entrada) throws IOException {
        List<Despesa> despesas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int[] contadores = new int[4];   // semParlamentar, semData, semValor, malformadas
        Colunas[] cabecalho = new Colunas[1];
        int[] lidas = new int[1];

        new LeitorCsv(';').ler(entrada, linha -> {
            if (cabecalho[0] == null) {
                cabecalho[0] = Colunas.doCabecalho(linha);
                OBRIGATORIAS.forEach(cabecalho[0]::posicao);
                return;
            }
            lidas[0]++;

            Colunas colunas = cabecalho[0];
            String id = colunas.texto(linha, "ideCadastro");
            if (id.isBlank()) {
                contadores[0]++;
                return;
            }

            String dataBruta = colunas.texto(linha, "datEmissao");
            LocalDate data = lerData(dataBruta);
            if (data == null) {
                contadores[1]++;
                avisar(avisos, "linha " + lidas[0] + ": data de emissão ilegível (\"" + dataBruta + "\")");
                return;
            }

            long centavos;
            try {
                centavos = Moeda.paraCentavos(colunas.texto(linha, "vlrLiquido"));
            } catch (IllegalArgumentException e) {
                contadores[2]++;
                avisar(avisos, "linha " + lidas[0] + ": " + e.getMessage());
                return;
            }
            if (centavos <= 0) {
                contadores[2]++;
                return;
            }

            try {
                despesas.add(new Despesa(
                        Long.parseLong(id),
                        colunas.texto(linha, "txNomeParlamentar"),
                        colunas.texto(linha, "sgPartido"),
                        colunas.texto(linha, "sgUF"),
                        colunas.texto(linha, "txtFornecedor"),
                        somenteDigitos(colunas.texto(linha, "txtCNPJCPF")),
                        colunas.texto(linha, "txtDescricao"),
                        colunas.texto(linha, "txtDescricaoEspecificacao"),
                        data,
                        centavos,
                        colunas.texto(linha, "txtNumero"),
                        colunas.texto(linha, "urlDocumento")));
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                contadores[3]++;
                avisar(avisos, "linha " + lidas[0] + ": " + e.getMessage());
            }
        });

        if (cabecalho[0] == null) {
            throw new IOException("O arquivo está vazio: não encontrei nem o cabeçalho.");
        }
        if (despesas.isEmpty()) {
            throw new IOException("Nenhuma despesa foi lida. O formato do arquivo da Câmara mudou.");
        }

        return new Resultado(List.copyOf(despesas), lidas[0], contadores[0], contadores[1],
                contadores[2], contadores[3], List.copyOf(avisos));
    }

    private static void avisar(List<String> avisos, String texto) {
        if (avisos.size() < 10) {
            avisos.add(texto);
        }
    }

    /** "2026-01-08T00:00:00" vira 2026-01-08; vazio ou torto vira {@code null}. */
    private static LocalDate lerData(String bruta) {
        if (bruta == null || bruta.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(bruta.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "085.324.290/0013-1" vira "08532429000131", para agrupar fornecedor sem erro de pontuação. */
    static String somenteDigitos(String documento) {
        StringBuilder digitos = new StringBuilder(documento.length());
        for (int i = 0; i < documento.length(); i++) {
            char c = documento.charAt(i);
            if (c >= '0' && c <= '9') {
                digitos.append(c);
            }
        }
        return digitos.toString();
    }
}
