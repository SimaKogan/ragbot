package dev.ragbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminTokenFilterTest {
    private final AdminTokenFilter filter = new AdminTokenFilter(new AppProps("secret", null, null, null));

    private int status(String uri, String token) throws Exception {
        var req = new MockHttpServletRequest("GET", uri);
        if (token != null) req.addHeader("X-Admin-Token", token);
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test void rejectsMissingToken() throws Exception { assertEquals(401, status("/api/stats", null)); }
    @Test void acceptsValidToken() throws Exception { assertEquals(200, status("/api/stats", "secret")); }
    @Test void slackEndpointsBypassFilter() throws Exception { assertEquals(200, status("/api/slack/events", null)); }
    @Test void telegramWebhookBypassesFilter() throws Exception { assertEquals(200, status("/api/telegram/webhook", null)); }
}
