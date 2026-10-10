package dev.radardacota.fonte;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Um arquivo da Câmara pequeno, montado na hora, com os mesmos 32 campos do
 * arquivo real.
 *
 * <p>Serve para dois propósitos. O primeiro é o CI: o GitHub Actions roda o
 * projeto inteiro a cada mudança, e depender do portal da Câmara para isso
 * significaria um build que quebra quando o portal cai, fica lento ou muda.
 * Com esta amostra, o teste de ponta a ponta roda offline em um segundo.
 *
 * <p>O segundo é a legibilidade: a amostra tem exatamente um caso de cada
 * regra, com valores escolhidos à mão. Quem for ler uma regra pode rodar o
 * demo e ver o alerta que ela deveria produzir — o que é bem mais útil do que
 * um teste que só verifica se a lista não está vazia.
 *
 * <p>Os dados são inventados. Qualquer semelhança com despesa real é coincidência.
 */
public final class AmostraDemo {

    private static final String CABECALHO = String.join(";",
            "txNomeParlamentar", "cpf", "ideCadastro", "nuCarteiraParlamentar", "nuLegislatura",
            "sgUF", "sgPartido", "codLegislatura", "numSubCota", "txtDescricao",
            "numEspecificacaoSubCota", "txtDescricaoEspecificacao", "txtFornecedor", "txtCNPJCPF",
            "txtNumero", "indTipoDocumento", "datEmissao", "vlrDocumento", "vlrGlosa", "vlrLiquido",
            "numMes", "numAno", "numParcela", "txtPassageiro", "txtTrecho", "numLote",
            "numRessarcimento", "datPagamentoRestituicao", "vlrRestituicao", "nuDeputadoId",
            "ideDocumento", "urlDocumento");

    private static final String COMBUSTIVEL = "COMBUSTÍVEIS E LUBRIFICANTES.";
    private static final String ESCRITORIO = "MANUTENÇÃO DE ESCRITÓRIO DE APOIO À ATIVIDADE PARLAMENTAR";
    private static final String DIVULGACAO = "DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR.";

    /** Notas de combustível na faixa normal. O volume é o que dá sentido ao percentil. */
    private static final int NOTAS_NORMAIS_DE_COMBUSTIVEL = 300;
    private static final int NOTAS_NORMAIS_DE_DIVULGACAO = 60;

    private AmostraDemo() {
    }

    public static String csv() {
        StringBuilder csv = new StringBuilder(CABECALHO).append('\n');

        // A massa de fundo: centenas de notas pequenas, de gente variada. É ela
        // que define o "padrão" da subcota — sem massa, não existe atípico.
        for (int i = 0; i < NOTAS_NORMAIS_DE_COMBUSTIVEL; i++) {
            csv.append(linha(campos(
                    "txNomeParlamentar", "Deputado Comum " + (i % 8 + 1),
                    "ideCadastro", String.valueOf(90000 + i % 8),
                    "sgUF", new String[]{"SP", "MG", "BA", "RS"}[i % 4],
                    "sgPartido", new String[]{"PT", "PL", "PSDB", "MDB"}[i % 4],
                    "txtDescricao", COMBUSTIVEL,
                    "txtFornecedor", "POSTO COMUM " + (i % 5) + " LTDA",
                    "txtCNPJCPF", "11.111.111/0001-" + String.format("%02d", i % 5),
                    "txtNumero", String.valueOf(1000 + i),
                    "datEmissao", dia(2026, i % 12 + 1, i % 27 + 1),
                    "vlrLiquido", String.valueOf(120 + (i * 5) % 400),
                    "urlDocumento", "https://www.camara.leg.br/nota/" + i))).append('\n');
        }

        for (int i = 0; i < NOTAS_NORMAIS_DE_DIVULGACAO; i++) {
            csv.append(linha(campos(
                    "txNomeParlamentar", "Deputado Comum " + (i % 6 + 1),
                    "ideCadastro", String.valueOf(90000 + i % 6),
                    "sgUF", new String[]{"SP", "MG", "BA"}[i % 3],
                    "sgPartido", new String[]{"PT", "PL", "PSDB"}[i % 3],
                    "txtDescricao", DIVULGACAO,
                    "txtFornecedor", "GRAFICA COMUM " + (i % 4) + " LTDA",
                    "txtCNPJCPF", "44.444.444/0001-" + String.format("%02d", i % 4),
                    "txtNumero", String.valueOf(3000 + i),
                    "datEmissao", dia(2026, i % 12 + 1, i % 27 + 1),
                    "vlrLiquido", String.valueOf(2000 + (i * 137) % 8000),
                    "urlDocumento", "https://www.camara.leg.br/nota/div/" + i))).append('\n');
        }

        // 1) VALOR ATÍPICO — combustível de R$ 9.800 contra um padrão de R$ 519.
        csv.append(linha(campos(
                "txNomeParlamentar", "Deputada Fora do Padrão",
                "ideCadastro", "91001", "sgUF", "AC", "sgPartido", "PL",
                "txtDescricao", COMBUSTIVEL,
                "txtFornecedor", "POSTO DA RODOVIA LTDA",
                "txtCNPJCPF", "22.222.222/0001-22",
                "txtNumero", "777", "datEmissao", "2026-03-11",
                "vlrLiquido", "9800", "urlDocumento", "https://www.camara.leg.br/nota/atipica"))).append('\n');

        // 2) FORNECEDOR CONCENTRADO — 100% da subcota de escritório num CNPJ só,
        //    em 10 notas, somando R$ 72.000.
        for (int i = 0; i < 10; i++) {
            csv.append(linha(campos(
                    "txNomeParlamentar", "Deputado Monofornecedor",
                    "ideCadastro", "91002", "sgUF", "RJ", "sgPartido", "UNIÃO",
                    "txtDescricao", ESCRITORIO,
                    "txtFornecedor", "IMOBILIARIA ÚNICA LTDA",
                    "txtCNPJCPF", "33.333.333/0001-33",
                    "txtNumero", String.valueOf(2000 + i),
                    "datEmissao", dia(2026, i + 1, 5),
                    "vlrLiquido", "7200",
                    "urlDocumento", "https://www.camara.leg.br/nota/escritorio/" + i))).append('\n');
        }

        // 3) FRACIONAMENTO — quatro notas ao mesmo CNPJ no mesmo dia, R$ 48.000.
        for (int i = 0; i < 4; i++) {
            csv.append(linha(campos(
                    "txNomeParlamentar", "Deputado Fracionado",
                    "ideCadastro", "91003", "sgUF", "GO", "sgPartido", "PP",
                    "txtDescricao", DIVULGACAO,
                    "txtFornecedor", "AGENCIA QUATRO NOTAS LTDA",
                    "txtCNPJCPF", "55.555.555/0001-55",
                    "txtNumero", String.valueOf(4000 + i),
                    "datEmissao", "2026-07-14",
                    "vlrLiquido", "12000",
                    "urlDocumento", "https://www.camara.leg.br/nota/fracao/" + i))).append('\n');
        }

        // 4) DOCUMENTO REPETIDO — a nota nº 5150, de R$ 8.400, lançada duas vezes.
        for (int i = 0; i < 2; i++) {
            csv.append(linha(campos(
                    "txNomeParlamentar", "Deputado Nota Dupla",
                    "ideCadastro", "91004", "sgUF", "PE", "sgPartido", "PSB",
                    "txtDescricao", DIVULGACAO,
                    "txtFornecedor", "PRODUCOES DUPLICADAS LTDA",
                    "txtCNPJCPF", "66.666.666/0001-66",
                    "txtNumero", "5150", "datEmissao", "2026-05-20",
                    "vlrLiquido", "8400",
                    "urlDocumento", "https://www.camara.leg.br/nota/dupla"))).append('\n');
        }

        // Linha que o radar deve descartar: sem ideCadastro (despesa de órgão).
        csv.append(linha(campos(
                "txNomeParlamentar", "LID.GOV-CD", "txtDescricao", COMBUSTIVEL,
                "txtFornecedor", "POSTO COMUM 0 LTDA", "txtCNPJCPF", "11.111.111/0001-00",
                "txtNumero", "9999", "datEmissao", "2026-01-08", "vlrLiquido", "1467"))).append('\n');

        // Linha que o radar deve descartar: sem data de emissão.
        csv.append(linha(campos(
                "txNomeParlamentar", "Deputado Sem Data", "ideCadastro", "91005", "sgUF", "DF",
                "sgPartido", "NOVO", "txtDescricao", COMBUSTIVEL,
                "txtFornecedor", "POSTO COMUM 1 LTDA", "txtCNPJCPF", "11.111.111/0001-01",
                "txtNumero", "8888", "vlrLiquido", "300"))).append('\n');

        return csv.toString();
    }

    private static String dia(int ano, int mes, int dia) {
        return String.format(Locale.ROOT, "%04d-%02d-%02dT00:00:00", ano, mes, dia);
    }

    /** Pares "nome da coluna", valor. */
    private static Map<String, String> campos(String... nomeEValor) {
        if (nomeEValor.length % 2 != 0) {
            throw new IllegalArgumentException("Cada campo precisa de nome e valor.");
        }
        Map<String, String> valores = new LinkedHashMap<>();
        for (int i = 0; i < nomeEValor.length; i += 2) {
            valores.put(nomeEValor[i], nomeEValor[i + 1]);
        }
        return valores;
    }

    /** Monta uma linha completa, com as colunas que o radar não usa em branco. */
    private static String linha(Map<String, String> valores) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("txNomeParlamentar", "");
        campos.put("cpf", "");
        campos.put("ideCadastro", "");
        campos.put("nuCarteiraParlamentar", "");
        campos.put("nuLegislatura", "2023");
        campos.put("sgUF", "");
        campos.put("sgPartido", "");
        campos.put("codLegislatura", "57");
        campos.put("numSubCota", "1");
        campos.put("txtDescricao", "");
        campos.put("numEspecificacaoSubCota", "0");
        campos.put("txtDescricaoEspecificacao", "");
        campos.put("txtFornecedor", "");
        campos.put("txtCNPJCPF", "");
        campos.put("txtNumero", "");
        campos.put("indTipoDocumento", "0");
        campos.put("datEmissao", "");
        campos.put("vlrDocumento", "");
        campos.put("vlrGlosa", "0");
        campos.put("vlrLiquido", "");
        campos.put("numMes", "1");
        campos.put("numAno", "2026");
        campos.put("numParcela", "0");
        campos.put("txtPassageiro", "");
        campos.put("txtTrecho", "");
        campos.put("numLote", "");
        campos.put("numRessarcimento", "");
        campos.put("datPagamentoRestituicao", "");
        campos.put("vlrRestituicao", "");
        campos.put("nuDeputadoId", "");
        campos.put("ideDocumento", "");
        campos.put("urlDocumento", "");
        campos.putAll(valores);

        StringBuilder linha = new StringBuilder();
        for (String valor : campos.values()) {
            if (linha.length() > 0) {
                linha.append(';');
            }
            linha.append('"').append(valor.replace("\"", "\"\"")).append('"');
        }
        return linha.toString();
    }
}
