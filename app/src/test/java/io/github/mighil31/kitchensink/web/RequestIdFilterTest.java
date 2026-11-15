package io.github.mighil31.kitchensink.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesAnIdAndExposesItToLogsAndTheClient() throws Exception {
        AtomicReference<String> seenByLogs = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> seenByLogs.set(MDC.get("requestId")));

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f]{32}").isEqualTo(seenByLogs.get());
        assertThat(MDC.get("requestId")).as("cleared after the request").isNull();
    }

    /** The gateway's id is reused, so its logs and the app's share it. */
    @Test
    void reusesTheGatewaysId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "gw-7f3a.42_x");

        assertThat(run(request).getHeader("X-Request-Id")).isEqualTo("gw-7f3a.42_x");
    }

    /** Anything that isn't a plain id could forge log lines or flood them, so it's replaced. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "abc\nFAKE LOG LINE",
                "id with spaces",
                "<script>",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void replacesUnsafeIncomingIds(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", incoming);

        assertThat(run(request).getHeader("X-Request-Id")).matches("[0-9a-f]{32}");
    }

    private HttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
