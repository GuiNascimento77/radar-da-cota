package dev.radardacota;

import dev.radardacota.analise.Alerta;
import dev.radardacota.analise.Analisador;
import dev.radardacota.analise.Resumo;
import dev.radardacota.fonte.AmostraDemo;
import dev.radardacota.fonte.Baixador;
import dev.radardacota.fonte.RepositorioDespesas;
import dev.radardacota.saida.Data;
import dev.radardacota.saida.GeradorSite;
import dev.radardacota.saida.Moeda;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * O programa inteiro: busca o arquivo do ano, lê, aplica as regras e escreve o site.
 *
 * <pre>
 *   java -jar radar-da-cota.jar                       # ano corrente, do portal da Câmara
 *   java -jar radar-da-cota.jar --ano 2025            # outro ano
 *   java -jar radar-da-cota.jar --entrada Ano-2026.csv.zip
 *   java -jar radar-da-cota.jar --demo                # dados sintéticos, sem rede
 *   java -jar radar-da-cota.jar --saida site --max-alertas 200
 * </pre>
 *
 * <p>É o que o GitHub Actions roda todo dia. O modo {@code --demo} existe para
 * isso rodar offline: ele monta uma amostra na memória, passa pelo mesmo
 * caminho de código e gera a mesma página, sem depender do portal da Câmara
 * estar no ar.
 */
public final class Radar {

    private Radar() {
    }

    /** As opções de linha de comando, já validadas. */
    record Opcoes(int ano, Path entrada, Path saida, boolean demo, int maxAlertasNaPagina, boolean ajuda) {

        static Opcoes ler(String[] argumentos) {
            int ano = LocalDate.now().getYear();
            Path entrada = null;
            Path saida = Path.of("site");
            boolean demo = false;
            int maxAlertas = GeradorSite.MAXIMO_DE_ALERTAS_NA_PAGINA;
            boolean ajuda = false;

            for (int i = 0; i < argumentos.length; i++) {
                String argumento = argumentos[i];
                switch (argumento) {
                    case "--ano" -> ano = Integer.parseInt(valor(argumentos, ++i, "--ano"));
                    case "--entrada" -> entrada = Path.of(valor(argumentos, ++i, "--entrada"));
                    case "--saida" -> saida = Path.of(valor(argumentos, ++i, "--saida"));
                    case "--max-alertas" -> maxAlertas = Integer.parseInt(valor(argumentos, ++i, "--max-alertas"));
                    case "--demo" -> demo = true;
                    case "--ajuda", "-h", "--help" -> ajuda = true;
                    default -> throw new IllegalArgumentException(
                            "Não conheço a opção \"" + argumento + "\". Use --ajuda para ver as disponíveis.");
                }
            }
            return new Opcoes(ano, entrada, saida, demo, maxAlertas, ajuda);
        }

        private static String valor(String[] argumentos, int indice, String opcao) {
            if (indice >= argumentos.length) {
                throw new IllegalArgumentException("A opção " + opcao + " precisa de um valor.");
            }
            return argumentos[indice];
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.of("pt", "BR"));
        // Sem isto o log sai torto em terminal cujo encoding não é UTF-8 — e o
        // log do GitHub Actions é justamente onde alguém vai ler "Manuten??o".
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        Opcoes opcoes = Opcoes.ler(args);
        if (opcoes.ajuda()) {
            imprimirAjuda();
            return;
        }

        long relogio = System.nanoTime();
        System.out.println("Radar da Cota — cota parlamentar de " + opcoes.ano());

        RepositorioDespesas.Resultado leitura = ler(opcoes);
        relatarLeitura(leitura);

        LocalDate hoje = LocalDate.now();
        Resumo resumo = Resumo.de(leitura.despesas(), opcoes.ano(), hoje, 40);
        System.out.printf("  %,d deputados · %,d fornecedores · %s%n",
                resumo.deputados(), resumo.fornecedores(), Moeda.formatar(resumo.totalCentavos()));

        Analisador analisador = new Analisador();
        List<Alerta> alertas = analisador.analisar(leitura.despesas());
        var contagem = analisador.contarPorRegra(alertas);

        System.out.println("Alertas por regra:");
        analisador.regras().forEach(regra -> System.out.printf("  %-24s %5d%n",
                regra.nome(), contagem.get(regra.nome())));
        System.out.println("  " + "-".repeat(30));
        System.out.printf("  %-24s %5d%n", "total", alertas.size());

        new GeradorSite(opcoes.maxAlertasNaPagina())
                .gerar(opcoes.saida(), resumo, alertas, analisador.regras(), contagem);

        System.out.println();
        System.out.println("Os dez casos mais graves:");
        alertas.stream().limit(10).forEach(alerta -> System.out.printf("  [%s] %s — %s%n",
                alerta.regra(), Moeda.formatar(alerta.centavos()), alerta.titulo()));

        System.out.println();
        System.out.println("Site em " + opcoes.saida().toAbsolutePath().resolve("index.html"));
        System.out.printf("Levou %.1f s.%n", Duration.ofNanos(System.nanoTime() - relogio).toMillis() / 1000.0);
    }

    private static RepositorioDespesas.Resultado ler(Opcoes opcoes) throws Exception {
        if (opcoes.demo()) {
            System.out.println("Modo demo: amostra sintética na memória, sem acesso à rede.");
            try (Reader entrada = new StringReader(AmostraDemo.csv())) {
                return RepositorioDespesas.carregar(entrada);
            }
        }
        if (opcoes.entrada() != null) {
            System.out.println("Lendo " + opcoes.entrada().toAbsolutePath());
            try (Reader entrada = Baixador.abrirLocal(opcoes.entrada())) {
                return RepositorioDespesas.carregar(entrada);
            }
        }
        System.out.println("Baixando " + Baixador.enderecoDoAno(opcoes.ano()));
        try (Reader entrada = Baixador.abrirDoAno(opcoes.ano())) {
            return RepositorioDespesas.carregar(entrada);
        }
    }

    private static void relatarLeitura(RepositorioDespesas.Resultado leitura) {
        System.out.printf("  %,d linhas lidas · %,d despesas aproveitadas · %,d descartadas%n",
                leitura.linhasLidas(), leitura.despesas().size(), leitura.descartadas());
        System.out.printf("    sem parlamentar %,d · sem data %,d · sem valor %,d · malformadas %,d%n",
                leitura.semParlamentar(), leitura.semData(), leitura.semValor(), leitura.malformadas());
        leitura.avisos().forEach(aviso -> System.out.println("    aviso: " + aviso));
    }

    private static void imprimirAjuda() {
        System.out.println("""
                Radar da Cota — aponta os gastos fora do padrão na cota parlamentar.

                Uso:
                  radar-da-cota [opções]

                Opções:
                  --ano ANO            ano do arquivo a baixar (padrão: ano corrente)
                  --entrada CAMINHO    usa um arquivo local, .csv ou .zip, em vez de baixar
                  --saida PASTA        onde gravar o site (padrão: site)
                  --demo               usa uma amostra sintética na memória, sem rede
                  --max-alertas N      quantos alertas embutir na página (padrão: 400)
                  --ajuda              mostra esta ajuda

                Exemplos:
                  java -jar radar-da-cota.jar --ano 2026
                  java -jar radar-da-cota.jar --demo --saida /tmp/site-demo

                Saída: index.html (a página), dados.json (os totais) e
                alertas.json (todos os alertas, sem corte).
                """);
    }
}
