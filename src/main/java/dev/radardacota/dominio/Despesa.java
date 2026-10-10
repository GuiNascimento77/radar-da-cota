package dev.radardacota.dominio;

import java.time.LocalDate;

/**
 * Uma despesa da Cota para o Exercicio da Atividade Parlamentar (CEAP).
 *
 * <p>O dinheiro vive em {@code long} de centavos, nunca em {@code double}: somar
 * centenas de milhares de valores quebrados com ponto flutuante acumula erro, e
 * aqui o numero vai virar manchete.
 *
 * @param deputadoId   id do deputado no portal da Camara
 * @param deputado     nome parlamentar
 * @param partido      sigla do partido
 * @param uf           unidade federativa
 * @param fornecedor   quem recebeu
 * @param cnpj         documento do fornecedor (CPF ou CNPJ, como vem na base)
 * @param categoria    a "subcota" (combustivel, passagem, alimentacao...)
 * @param especificacao detalhe dentro da subcota
 * @param data         data de emissao do documento
 * @param centavos     valor liquido pago
 * @param documento    numero do documento
 * @param urlDocumento link para o PDF da nota fiscal
 */
public record Despesa(long deputadoId,
                      String deputado,
                      String partido,
                      String uf,
                      String fornecedor,
                      String cnpj,
                      String categoria,
                      String especificacao,
                      LocalDate data,
                      long centavos,
                      String documento,
                      String urlDocumento) {

    public Despesa {
        if (centavos < 0) {
            throw new IllegalArgumentException("Valor negativo nao faz sentido numa despesa.");
        }
        if (data == null) {
            throw new IllegalArgumentException("Despesa sem data nao entra no radar.");
        }
    }

    /** Chave do "quem gastou com quem no mesmo dia" usada na regra de fracionamento. */
    public String chaveDeputadoFornecedorDia() {
        return deputadoId + "|" + cnpj + "|" + data;
    }

    /** Mes e ano, para agrupar o resumo. */
    public String mesAno() {
        return String.format("%04d-%02d", data.getYear(), data.getMonthValue());
    }

    public Despesa somar(long valorCentavos) {
        return new Despesa(deputadoId, deputado, partido, uf, fornecedor, cnpj, categoria,
                especificacao, data, centavos + valorCentavos, documento, urlDocumento);
    }
}
