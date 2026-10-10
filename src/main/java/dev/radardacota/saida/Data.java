package dev.radardacota.saida;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Datas e meses em português, sem depender de locale do sistema. */
public final class Data {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MESES = {
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    };

    private Data() {
    }

    /** 19/02/2026 */
    public static String formatar(LocalDate data) {
        return data == null ? "" : DIA.format(data);
    }

    /** 3 vira "março". */
    public static String mesPorExtenso(int mes) {
        return mes >= 1 && mes <= 12 ? MESES[mes - 1] : "mês " + mes;
    }

    /** "2026-03" vira "março/2026". */
    public static String mesAnoLegivel(String mesAno) {
        if (mesAno == null || mesAno.length() < 7) {
            return mesAno == null ? "" : mesAno;
        }
        int mes = Integer.parseInt(mesAno.substring(5, 7));
        return mesPorExtenso(mes) + "/" + mesAno.substring(0, 4);
    }
}
