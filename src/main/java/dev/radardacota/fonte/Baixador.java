package dev.radardacota.fonte;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Busca o arquivo do ano no portal da Câmara e devolve o CSV pronto para leitura.
 *
 * <p>O arquivo vem zipado ({@code Ano-2026.csv.zip}, cerca de 4 MB) e vira um CSV
 * de 43 MB. Nada é gravado em disco no caminho normal: o corpo da resposta é
 * descompactado enquanto chega e vai direto para o leitor, então o processo
 * ocupa poucos megabytes mesmo com o arquivo inteiro passando por ele.
 *
 * <p>Sem biblioteca de HTTP: o {@link HttpClient} do próprio JDK resolve. É uma
 * dependência de execução a menos para alguém auditar.
 *
 * <p>O {@code User-Agent} não é decorativo: o portal fica atrás de um filtro que
 * devolve 403 para requisições sem identificação. Como este radar se identifica
 * e aponta para o repositório, quem administra o portal consegue saber quem
 * está batendo à porta — que é o mínimo ao consumir dados públicos de alguém.
 */
public final class Baixador {

    private static final String ENDERECO = "https://www.camara.leg.br/cotas/Ano-%d.csv.zip";

    private static final String AGENTE =
            "radar-da-cota/1.0 (+https://github.com/GuiNascimento77/radar-da-cota)";

    private Baixador() {
    }

    public static String enderecoDoAno(int ano) {
        return String.format(ENDERECO, ano);
    }

    /** Abre o CSV do ano direto do portal, sem gravar em disco. */
    public static Reader abrirDoAno(int ano) throws IOException, InterruptedException {
        HttpClient cliente = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(enderecoDoAno(ano)))
                .header("User-Agent", AGENTE)
                .header("Accept", "application/zip,text/csv,*/*")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<InputStream> resposta =
                cliente.send(requisicao, HttpResponse.BodyHandlers.ofInputStream());

        if (resposta.statusCode() == 404) {
            throw new IOException("O portal da Câmara ainda não publicou o arquivo de " + ano
                    + ". Endereço consultado: " + enderecoDoAno(ano));
        }
        if (resposta.statusCode() != 200) {
            throw new IOException("O portal da Câmara respondeu HTTP " + resposta.statusCode()
                    + " em " + enderecoDoAno(ano) + ".");
        }

        return descompactar(resposta.body(), "Ano-" + ano + ".csv.zip");
    }

    /** Abre um arquivo já baixado — {@code .zip} ou {@code .csv} puro. */
    public static Reader abrirLocal(Path arquivo) throws IOException {
        if (!Files.isReadable(arquivo)) {
            throw new IOException("Não consegui ler o arquivo " + arquivo.toAbsolutePath() + ".");
        }
        if (arquivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            return descompactar(Files.newInputStream(arquivo), arquivo.getFileName().toString());
        }
        return Files.newBufferedReader(arquivo, StandardCharsets.UTF_8);
    }

    /** Pega a primeira entrada do zip e devolve o texto dela como leitor. */
    private static Reader descompactar(InputStream corpo, String origem) throws IOException {
        ZipInputStream zip = new ZipInputStream(corpo, StandardCharsets.UTF_8);
        ZipEntry entrada = zip.getNextEntry();
        while (entrada != null && entrada.isDirectory()) {
            entrada = zip.getNextEntry();
        }
        if (entrada == null) {
            throw new IOException("O arquivo " + origem + " chegou sem nenhum CSV dentro.");
        }
        return new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8), 1 << 16);
    }
}
