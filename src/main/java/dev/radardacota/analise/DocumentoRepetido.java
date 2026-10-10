package dev.radardacota.analise;

import dev.radardacota.dominio.Despesa;
import dev.radardacota.saida.Data;
import dev.radardacota.saida.Moeda;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A mesma nota fiscal lançada mais de uma vez.
 *
 * <p>É o caso mais direto que existe na base: mesmo parlamentar, mesmo CNPJ,
 * <b>mesmo número de documento</b>, mesma data de emissão e exatamente o mesmo
 * valor, repetido. Ou a nota foi lançada em duplicidade — e aí houve pagamento
 * em dobro —, ou duas despesas diferentes foram registradas sob o mesmo número,
 * o que quebra a rastreabilidade da prestação de contas. Nos dois casos, alguém
 * precisa olhar o documento.
 *
 * <p>Notas sem número de verdade ficam de fora: a base traz "SN", "S/N" e vazio
 * em muitos lançamentos, e comparar esses registros entre si não diz nada —
 * quando várias dessas notas somam alto no mesmo dia, quem pega é a regra de
 * fracionamento.
 *
 * <p>A gravidade é a mais alta do conjunto porque, ao contrário das outras
 * regras, aqui não existe explicação corriqueira.
 */
public final class DocumentoRepetido implements Regra {

    /** Números que não identificam documento nenhum. */
    private static final Set<String> SEM_NUMERO = Set.of("", "0", "-", "SN", "S/N", "S N", "SEM NUMERO");

    @Override
    public String nome() {
        return "documento-repetido";
    }

    @Override
    public String titulo() {
        return "Documento repetido";
    }

    @Override
    public String pergunta() {
        return "Esta nota aparece lançada mais de uma vez para o mesmo parlamentar?";
    }

    @Override
    public String criterio() {
        return "Mesmo parlamentar, mesmo CNPJ, mesmo número de documento, mesma data e "
                + "exatamente o mesmo valor em duas ou mais linhas da base. "
                + "Notas sem número real (SN, S/N, vazio) são ignoradas.";
    }

    @Override
    public List<Alerta> analisar(List<Despesa> despesas) {
        Map<String, List<Despesa>> porNota = new HashMap<>();
        for (Despesa despesa : despesas) {
            if (SEM_NUMERO.contains(despesa.documento().toUpperCase(Locale.ROOT))) {
                continue;
            }
            String chave = despesa.deputadoId() + "|" + despesa.cnpj() + "|"
                    + despesa.documento() + "|" + despesa.data() + "|" + despesa.centavos();
            porNota.computeIfAbsent(chave, k -> new ArrayList<>()).add(despesa);
        }

        List<Alerta> alertas = new ArrayList<>();
        for (List<Despesa> linhas : porNota.values()) {
            if (linhas.size() < 2) {
                continue;
            }
            linhas.sort(Comparator.comparingLong(Despesa::centavos).reversed());

            Despesa primeira = linhas.get(0);
            long soma = primeira.centavos() * linhas.size();
            long pagoAMais = primeira.centavos() * (linhas.size() - 1);

            String titulo = "Nota " + primeira.documento() + " lançada "
                    + linhas.size() + " vezes";
            String detalhe = "A nota nº " + primeira.documento() + " de "
                    + primeira.fornecedor() + ", no valor de "
                    + Moeda.formatar(primeira.centavos()) + " e emitida em "
                    + Data.formatar(primeira.data()) + ", aparece " + linhas.size()
                    + " vezes na base em nome de " + primeira.deputado() + " ("
                    + primeira.partido() + "-" + primeira.uf() + "). Somadas, as linhas dão "
                    + Moeda.formatar(soma) + " — " + Moeda.formatar(pagoAMais)
                    + " acima do valor de uma nota só.";

            alertas.add(new Alerta(nome(), 3, titulo, detalhe, soma, linhas));
        }
        return alertas;
    }
}
