package ci.orange.subscription.infrastructure.web.common;

import ci.orange.subscription.application.port.in.BatchedExport;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

/**
 * Écrit un export au format CSV, au fil de la lecture des lots (ADR-0020). Le format vise Excel en
 * français : séparateur point-virgule (la virgule y est le séparateur décimal), UTF-8 précédé d'une
 * marque d'ordre des octets (sans elle, Excel affiche mal les accents), fins de ligne CRLF.
 */
public final class CsvExport {

	public static final String MEDIA_TYPE = "text/csv;charset=UTF-8";

	private static final char SEPARATOR = ';';
	private static final String LINE_END = "\r\n";
	private static final char BYTE_ORDER_MARK = '\uFEFF';
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private CsvExport() {
	}

	/**
	 * Envoie l'en-tête, puis chaque élément de l'export, lot par lot ; renvoie le nombre de lignes de
	 * données écrites.
	 */
	public static <T> long write(HttpServletResponse response, String fileName, List<String> columns,
			BatchedExport<T> export, Function<T, List<String>> toRow) throws IOException {
		response.setContentType(MEDIA_TYPE);
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(fileName).build().toString());
		Writer writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
		writer.write(BYTE_ORDER_MARK);
		writeLine(writer, columns);
		long rows = export.forEachBatch(batch -> {
			for (T item : batch) {
				writeLine(writer, toRow.apply(item));
			}
		});
		writer.flush();
		return rows;
	}

	/** 25000.00 donne « 25000,00 » : un nombre qu'Excel en français sait additionner. */
	public static String amount(BigDecimal value) {
		return value == null ? "" : value.toPlainString().replace('.', ',');
	}

	public static String date(LocalDate value) {
		return value == null ? "" : DATE.format(value);
	}

	public static String dateTime(Instant value, ZoneId zone) {
		return value == null ? "" : DATE_TIME.format(value.atZone(zone));
	}

	/**
	 * « 0701020304 » donne « 07 01 02 03 04 » : groupé ainsi, Excel garde le numéro comme du texte au
	 * lieu de le convertir en nombre, ce qui ferait disparaître le 0 initial.
	 */
	public static String phone(String digits) {
		if (digits == null) {
			return "";
		}
		List<String> pairs = new ArrayList<>();
		for (int i = 0; i < digits.length(); i += 2) {
			pairs.add(digits.substring(i, Math.min(i + 2, digits.length())));
		}
		return String.join(" ", pairs);
	}

	/**
	 * Noms des filtres renseignés, pour tracer un export sans écrire leurs valeurs dans les logs :
	 * elles peuvent contenir des données personnelles. Les arguments alternent nom et valeur.
	 */
	public static String usedFilters(Object... namesAndValues) {
		List<String> used = new ArrayList<>();
		for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
			Object value = namesAndValues[i + 1];
			if (value != null && !value.toString().isBlank()) {
				used.add(namesAndValues[i].toString());
			}
		}
		return used.isEmpty() ? "none" : String.join(", ", used);
	}

	/**
	 * Une valeur saisie par un utilisateur et commençant par =, +, - ou @ serait exécutée comme une
	 * formule à l'ouverture dans un tableur (injection CSV, voir OWASP) : elle est préfixée d'une
	 * apostrophe, qui la fait lire comme du texte. La valeur est ensuite mise entre guillemets si elle
	 * contient un séparateur, un guillemet ou un saut de ligne.
	 */
	static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String text = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
		boolean needsQuotes = text.indexOf(SEPARATOR) >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
				|| text.indexOf('\r') >= 0;
		return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
	}

	private static void writeLine(Writer writer, List<String> values) {
		try {
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) {
					writer.write(SEPARATOR);
				}
				writer.write(cell(values.get(i)));
			}
			writer.write(LINE_END);
		}
		catch (IOException exception) {
			// Le client a interrompu le téléchargement : l'export s'arrête là
			throw new UncheckedIOException(exception);
		}
	}

}
