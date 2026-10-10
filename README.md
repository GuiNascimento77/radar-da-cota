# Radar da Cota

[![Atualizar o radar](https://github.com/GuiNascimento77/radar-da-cota/actions/workflows/atualizar.yml/badge.svg)](https://github.com/GuiNascimento77/radar-da-cota/actions/workflows/atualizar.yml)

**Site com os alertas: <https://guinascimento77.github.io/radar-da-cota/>**

Todo deputado federal recebe a Cota para o Exercício da Atividade Parlamentar
(CEAP) para pagar combustível, passagens, aluguel de escritório, divulgação e
mais uma dúzia de coisas. A Câmara publica cada nota fiscal num arquivo público.
São 114 mil linhas por ano.

Este projeto lê esse arquivo inteiro, calcula o que é padrão em cada subcota e
aponta as despesas que fogem dele. Nenhum nome é acusado de nada: um alerta diz
"esta nota está fora do padrão estatístico", não "esta nota é irregular".

Os números de 2026 até agora:

| | |
|---|---|
| Notas fiscais | 110.919 |
| Parlamentares | 563 |
| Fornecedores | 23.758 |
| Total pago | R$ 137.331.734,21 |
| Alertas | **1.182** |

## As quatro regras

Cada alerta nasce de uma regra, e cada regra tem um critério que dá para
conferir na mão. Os limites foram calibrados contra a base real de 2026, não
escolhidos no chute.

| Regra | O que procura | Gravidade | Casos em 2026 |
|---|---|---|---|
| **Valor atípico** | nota acima do percentil 99,5 da própria subcota, e acima de R$ 1.000 | 2 | 511 |
| **Fornecedor concentrado** | 80% ou mais do gasto do parlamentar numa categoria indo para um fornecedor só, com pelo menos 3 notas e R$ 20.000 | 3 | 597 |
| **Fracionamento** | 2 ou mais notas para o mesmo fornecedor no mesmo dia, somando mais de 3 vezes o percentil 99 da subcota (mínimo R$ 5.000) | 2 | 14 |
| **Documento repetido** | a mesma nota fiscal lançada mais de uma vez (mesmo documento, mesmo valor, mesmo dia) | 3 | 60 |

Detalhes que importam:

- **Valor atípico** só roda em subcotas com pelo menos 50 notas. Subcota rara tem
  percentil instável e viraria gerador de falso positivo.
- **Fornecedor concentrado** compara dentro de `parlamentar + subcota`, porque
  100% de combustível num posto é normal e 100% de divulgação numa empresa não é.
- **Fracionamento** usa o percentil da subcota como referência porque o teto de
  cada subcota é diferente: R$ 9.000 num posto é comum, R$ 9.000 de divulgação não.
- **Documento repetido** ignora notas sem número (`SN`, `S/N`, `0`, `-`). Sem esse
  filtro o número de casos sobe de 60 para 155, e 95 desses são ruído.

## Exemplos reais

Saídos do arquivo de 2026:

- R$ 402.000,00 em divulgação para um fornecedor só, em 8 notas ao longo do ano.
- R$ 255.000,00 em locação de aeronaves para um fornecedor só, em 5 notas.
- Duas notas de R$ 53.000,00 para o mesmo fornecedor no mesmo dia.
- Três notas de R$ 9.000,00 no mesmo posto, no mesmo dia, logo abaixo do teto da
  subcota.

Os nomes estão no site, com link para cada nota fiscal no portal da Câmara.

## Como rodar

Precisa só de Java 21. Não há nenhuma dependência de execução: o cliente HTTP é
o do próprio JDK, o leitor de CSV é escrito aqui e o HTML e o JSON são montados
na mão. As únicas dependências são de teste (JUnit 5).

```bash
mvn -B test                                        # 106 testes
mvn -B package
java -jar target/radar-da-cota.jar --demo --saida site
```

O `--demo` gera o site a partir de uma amostra sintética, sem tocar a internet.
É o que roda no CI a cada push, para garantir que o gerador não quebrou.

Contra o arquivo real:

```bash
java -jar target/radar-da-cota.jar --ano 2026 --saida site
```

| Opção | Para que serve |
|---|---|
| `--ano 2026` | qual ano baixar (padrão: o ano corrente) |
| `--entrada arquivo.zip` | usar um arquivo já baixado, sem acessar a rede |
| `--saida pasta` | onde gravar o site (padrão: `site`) |
| `--max-alertas 400` | quantos alertas entram embutidos na página |
| `--demo` | rodar com a amostra sintética |
| `--ajuda` | mostrar as opções |

O arquivo do ano tem 43 MB e descompacta em memória, sem gravar em disco. O
processo inteiro leva cerca de 4 segundos.

## De onde vêm os dados

- Notas fiscais: `https://www.camara.leg.br/cotas/Ano-{ano}.csv.zip`, o arquivo
  público da CEAP, em UTF-8, separado por ponto e vírgula, 32 colunas.
- Nome, partido e UF vêm das próprias linhas do arquivo.

A API `/api/v2/deputados/{id}/despesas` **não** é usada: no momento em que este
projeto foi escrito ela responde `200` com `{"dados": []}` para qualquer
parlamentar e qualquer ano.

## Limites conhecidos

- O arquivo traz 2.554 linhas sem data de emissão e 536 sem parlamentar (gastos
  de lideranças e órgãos). Elas são descartadas e contadas no relatório.
- Um alerta é desvio de padrão, não irregularidade. Uma passagem aérea cara pode
  ser o único voo disponível; uma nota repetida pode ser erro de digitação de
  quem lançou, não do parlamentar.
- As regras olham valor, data e fornecedor. Elas não leem a nota fiscal, não
  sabem o que foi comprado e não julgam se o preço era justo.
- O teto da cota varia com a distância até Brasília, então comparar total entre
  estados não diz nada por si.

## Estrutura

```
src/main/java/dev/radardacota/
  dominio/    Despesa (o registro que circula pelo resto do código)
  fonte/      download, leitura do CSV, mapeamento das colunas
  analise/    as quatro regras, a ordenação e os resumos
  saida/      dinheiro em centavos, texto, JSON, gerador do site
  Radar.java  linha de comando
src/main/resources/site/modelo.html   a página (um HTML só, sem CDN)
```

Dinheiro é `long` em centavos e é lido com `BigDecimal`, nunca `double`. O
`vlrLiquido` do arquivo da Câmara usa ponto como separador decimal
(`"114.74"` é R$ 114,74), o que em `double` vira erro de arredondamento silencioso.

A página é autocontida: sem CDN, sem `fetch`, sem build de front-end. Os dados vão
dentro de uma tag `<script type="application/json">`, e o JSON escapa `<`, `>` e
`&` como `\u003c`, `\u003e` e `\u0026` para que nenhum texto consiga fechar a tag.
O arquivo funciona aberto direto do disco, no GitHub Pages ou dentro de um e-mail.

## Licença

MIT. Os dados são públicos, do portal de dados abertos da Câmara dos Deputados.

---

# Radar da Cota (English)

A monitor for Brazilian congressional expense allowances (CEAP). Every federal
deputy gets a monthly allowance to pay for fuel, flights, office rent, publicity
and a dozen other things, and the Chamber of Deputies publishes every receipt as
a public CSV. This project reads the whole file, learns what is normal for each
expense category and flags what falls outside it.

Live site: <https://guinascimento77.github.io/radar-da-cota/>

**Four rules**, each with a threshold you can check by hand, calibrated against
the real 2026 data (110,919 receipts, 563 deputies, R$ 137 million):

| Rule | What it looks for | Cases in 2026 |
|---|---|---|
| **Unusual amount** | a receipt above the 99.5th percentile of its own category | 511 |
| **Concentrated supplier** | 80%+ of a deputy's spending in one category going to a single supplier | 597 |
| **Splitting** | two or more receipts to the same supplier on the same day, together above 3x the category's 99th percentile | 14 |
| **Duplicate receipt** | the same receipt filed more than once | 60 |

Java 21, no runtime dependencies (JDK HTTP client, hand-written CSV parser,
hand-built HTML and JSON), JUnit 5 for tests. `java -jar target/radar-da-cota.jar --demo`
builds the site from a synthetic sample with no network access.

A flag means a statistical outlier, not an accusation. The site says so on every
page.

MIT licensed. Data: Brazilian Chamber of Deputies open data portal.
