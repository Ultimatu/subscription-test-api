package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

	@Mock
	private AuditTrail auditTrail;

	private AuditService auditService;

	@BeforeEach
	void createService() {
		auditService = new AuditService(auditTrail,
				Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneId.of("Africa/Abidjan")));
	}

	@Test
	void searchesWholeDaysInAbidjan() {
		AuditSearchCriteria criteria = new AuditSearchCriteria(null, null, null, null, LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 15));

		auditService.search(criteria, PageRequest.of(0, 20));

		verify(auditTrail).search(criteria, Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-10-16T00:00:00Z"), PageRequest.of(0, 20));
	}

	@Test
	void searchesWithoutPeriod() {
		AuditSearchCriteria criteria = new AuditSearchCriteria(null, null, "admin", null, null, null);

		auditService.search(criteria, PageRequest.of(0, 20));

		verify(auditTrail).search(any(), isNull(), isNull(), any());
	}

	@Test
	void refusesAnInvertedPeriod() {
		assertThatThrownBy(() -> auditService.search(new AuditSearchCriteria(null, null, null, null,
				LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1)), PageRequest.of(0, 20)))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_DATE_RANGE);
		verifyNoInteractions(auditTrail);
	}

}
