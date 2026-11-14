package br.com.avisabrasil.avisos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Lê os avisos do INMET.
 * <ul>
 *   <li>RSS ({@code /avisos/rss}): lista todos os avisos, com as áreas por mesorregião. Serve para escolher
 *       quais avisos abrir.</li>
 *   <li>CAP ({@code /avisos/rss/{id}}): o aviso completo, com o parâmetro "Municipios" (nome e código do
 *       IBGE). É ele que decide se o aviso vale para a comunidade.</li>
 * </ul>
 */
public final class InmetConversor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_RSS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]");
    private static final Pattern ID_NO_LINK = Pattern.compile("/(\\d+)\\s*$");

    private InmetConversor() {
    }

    /** Números dos avisos do RSS cuja área inclui a mesorregião e que ainda não tinham terminado em {@code desde}. */
    public static List<String> candidatos(String rss, String mesorregiao, Instant desde) {
        List<String> ids = new ArrayList<>();
        NodeList itens = ler(rss).getElementsByTagName("item");
        for (int i = 0; i < itens.getLength(); i++) {
            Element item = (Element) itens.item(i);
            String descricao = texto(item, "description");
            Matcher id = ID_NO_LINK.matcher(texto(item, "link"));
            if (!id.find() || !campoDaTabela(descricao, "Área").contains(mesorregiao)) {
                continue;
            }
            Optional<Instant> fim = dataDoRss(campoDaTabela(descricao, "Fim"));
            if (fim.isEmpty() || !fim.get().isBefore(desde)) {
                ids.add(id.group(1));
            }
        }
        return ids;
    }

    /** Converte o que o {@code InmetClient} guardou ({"caps": {id: xml}}) nos avisos que valem para o município. */
    public static List<AvisoInmet> converter(String json, String codigoIbge) {
        try {
            JsonNode caps = JSON.readTree(json).path("caps");
            List<AvisoInmet> avisos = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = caps.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> cap = it.next();
                lerCap(cap.getKey(), cap.getValue().asText(), codigoIbge).ifPresent(avisos::add);
            }
            return semRepetidos(avisos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * O INMET às vezes republica o mesmo aviso com outro número (mesmo evento, grau e fim). Fica só a versão
     * publicada por último (o maior número), para a lista não repetir o mesmo aviso.
     */
    static List<AvisoInmet> semRepetidos(List<AvisoInmet> avisos) {
        Map<String, AvisoInmet> porChave = new java.util.LinkedHashMap<>();
        for (AvisoInmet a : avisos) {
            String chave = a.eventoCodigo() + "|" + a.grau() + "|" + a.fim().toInstant();
            porChave.merge(chave, a, (x, y) -> Long.parseLong(x.id()) >= Long.parseLong(y.id()) ? x : y);
        }
        List<AvisoInmet> lista = new ArrayList<>(porChave.values());
        lista.sort(Comparator.comparing(AvisoInmet::inicio).reversed());
        return List.copyOf(lista);
    }

    /** Lê um aviso CAP; vazio se o aviso não inclui o município. */
    public static Optional<AvisoInmet> lerCap(String id, String xml, String codigoIbge) {
        Document doc = ler(xml);
        Element info = (Element) doc.getElementsByTagNameNS("*", "info").item(0);
        if (info == null || !valorDoParametro(info, "Municipios").contains("(" + codigoIbge + ")")) {
            return Optional.empty();
        }
        String evento = texto(info, "event");
        return Optional.of(new AvisoInmet(
                id,
                evento,
                codigoDoEvento(evento),
                grau(texto(info, "severity")),
                OffsetDateTime.parse(texto(info, "onset")),
                OffsetDateTime.parse(texto(info, "expires")),
                texto(info, "description"),
                texto(info, "instruction"),
                texto(info, "web")));
    }

    static AvisoInmet.Grau grau(String severidadeCap) {
        return switch (severidadeCap.toLowerCase(Locale.ROOT)) {
            case "moderate" -> AvisoInmet.Grau.PERIGO_POTENCIAL;
            case "severe" -> AvisoInmet.Grau.PERIGO;
            case "extreme" -> AvisoInmet.Grau.GRANDE_PERIGO;
            default -> null;
        };
    }

    /** "Declínio de Temperatura" → "DECLINIO_DE_TEMPERATURA". */
    static String codigoDoEvento(String evento) {
        return Normalizer.normalize(evento, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String valorDoParametro(Element info, String nome) {
        NodeList parametros = info.getElementsByTagNameNS("*", "parameter");
        for (int i = 0; i < parametros.getLength(); i++) {
            Element p = (Element) parametros.item(i);
            if (nome.equals(texto(p, "valueName"))) {
                return texto(p, "value");
            }
        }
        return "";
    }

    /** Valor de uma linha da tabela HTML que o INMET põe na descrição do RSS. */
    private static String campoDaTabela(String html, String campo) {
        Matcher m = Pattern.compile("<th[^>]*>\\s*" + Pattern.quote(campo) + "\\s*</th>\\s*<td>(.*?)</td>", Pattern.DOTALL)
                .matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    private static Optional<Instant> dataDoRss(String texto) {
        try {
            return Optional.of(LocalDateTime.parse(texto, DATA_RSS).atZone(BRASILIA).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String texto(Element pai, String nome) {
        NodeList nos = pai.getElementsByTagNameNS("*", nome);
        if (nos.getLength() == 0) {
            nos = pai.getElementsByTagName(nome);
        }
        return nos.getLength() == 0 ? "" : nos.item(0).getTextContent().trim();
    }

    /** XML sem DTD nem entidades externas (o conteúdo vem da internet). */
    private static Document ler(String xml) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            return fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("XML do INMET inválido: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
