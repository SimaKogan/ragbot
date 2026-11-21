package dev.ragbot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Защищает /api/** (кроме вебхука Telegram) токеном из заголовка X-Admin-Token. */
@Component
class AdminTokenFilter extends OncePerRequestFilter {
    private final AppProps props;
    AdminTokenFilter(AppProps props) { this.props = props; }

    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String p = r.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/telegram/") || p.startsWith("/api/slack/");
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (props.adminToken().equals(req.getHeader("X-Admin-Token"))) chain.doFilter(req, res);
        else res.sendError(401, "Invalid admin token");
    }
}
