package ci.orange.subscription.infrastructure.web.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTracingFilterTest {

	private final RequestTracingFilter filter = new RequestTracingFilter();

	private final AtomicReference<String> idSeenByTheRequest = new AtomicReference<>();

	private final FilterChain chain = (request, response) -> idSeenByTheRequest
			.set(MDC.get(RequestTracingFilter.REQUEST_ID_MDC_KEY));

	@Test
	void generatesAnIdWhenTheCallerSendsNone() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/api/clients"), response, chain);

		String requestId = response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER);
		assertThat(UUID.fromString(requestId)).isNotNull();
		assertThat(idSeenByTheRequest).hasValue(requestId);
	}

	@Test
	void reusesAValidIdSentByTheCaller() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
		request.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, "front-1234-abcd");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertThat(response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)).isEqualTo("front-1234-abcd");
		assertThat(idSeenByTheRequest).hasValue("front-1234-abcd");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc\nINFO forged log line", "id with spaces", "",
			"an-identifier-that-is-far-too-long-to-be-accepted-as-a-request-id-by-the-filter" })
	void replacesAnUnsafeIdSentByTheCaller(String unsafeId) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
		request.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, unsafeId);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		String requestId = response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER);
		assertThat(requestId).isNotEqualTo(unsafeId);
		assertThat(UUID.fromString(requestId)).isNotNull();
	}

	@Test
	void cleansTheLoggingContextAfterTheRequest() throws Exception {
		filter.doFilter(new MockHttpServletRequest("GET", "/api/clients"), new MockHttpServletResponse(), chain);

		assertThat(MDC.get(RequestTracingFilter.REQUEST_ID_MDC_KEY)).isNull();
	}

}
