package ci.orange.subscription.infrastructure.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.in.BatchedExport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class CsvExportTest {

	@Test
	void writesAnExcelFriendlyCsvBatchByBatch() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		BatchedExport<String> export = action -> {
			action.accept(List.of("Koné"));
			action.accept(List.of("Traoré"));
			return 2;
		};

		long rows = CsvExport.write(response, "clients-2026-10-15.csv", List.of("Nom", "Ville"), export,
				name -> List.of(name, "Abidjan"));

		assertThat(rows).isEqualTo(2);
		assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
		assertThat(response.getHeader("Content-Disposition")).startsWith("attachment;")
				.contains("filename=\"clients-2026-10-15.csv\"");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
				.isEqualTo("\uFEFFNom;Ville\r\nKoné;Abidjan\r\nTraoré;Abidjan\r\n");
	}

	@Test
	void writesTheHeaderEvenWhenNothingMatches() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();

		long rows = CsvExport.write(response, "vide.csv", List.of("Nom"), action -> 0, name -> List.of());

		assertThat(rows).isZero();
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("\uFEFFNom\r\n");
	}

	@Test
	void neutralisesFormulasSoThatSpreadsheetsReadThemAsText() {
		assertThat(CsvExport.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
		assertThat(CsvExport.cell("+33 1 23")).isEqualTo("'+33 1 23");
		assertThat(CsvExport.cell("-2+3")).isEqualTo("'-2+3");
		assertThat(CsvExport.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
		assertThat(CsvExport.cell("Koné")).isEqualTo("Koné");
	}

	@Test
	void quotesValuesContainingASeparatorAQuoteOrALineBreak() {
		assertThat(CsvExport.cell("Impayé; relance")).isEqualTo("\"Impayé; relance\"");
		assertThat(CsvExport.cell("dit \"urgent\"")).isEqualTo("\"dit \"\"urgent\"\"\"");
		assertThat(CsvExport.cell("ligne 1\nligne 2")).isEqualTo("\"ligne 1\nligne 2\"");
		assertThat(CsvExport.cell(null)).isEmpty();
	}

	@Test
	void formatsValuesForAFrenchSpreadsheet() {
		assertThat(CsvExport.amount(new BigDecimal("25000.00"))).isEqualTo("25000,00");
		assertThat(CsvExport.date(LocalDate.of(2026, 10, 5))).isEqualTo("05/10/2026");
		assertThat(CsvExport.dateTime(Instant.parse("2026-10-15T08:30:00Z"), ZoneId.of("Africa/Abidjan")))
				.isEqualTo("15/10/2026 08:30");
		assertThat(CsvExport.phone("0701020304")).isEqualTo("07 01 02 03 04");
		assertThat(CsvExport.amount(null)).isEmpty();
		assertThat(CsvExport.date(null)).isEmpty();
	}

	@Test
	void namesTheFiltersInUseWithoutTheirValues() {
		assertThat(CsvExport.usedFilters("city", "Abidjan", "status", null, "search", " ")).isEqualTo("city");
		assertThat(CsvExport.usedFilters("city", null)).isEqualTo("none");
	}

}
