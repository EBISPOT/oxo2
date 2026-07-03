package uk.ac.ebi.spot.oxo.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds {@code X-Content-Type-Options: nosniff} to every response.
 *
 * <p>OxO2 streams user-driven CSV/TSV exports (see the v1 {@code /api/search} and v2 export endpoints)
 * whose rows can echo caller-supplied identifiers. Those responses are already served with an explicit
 * {@code text/csv} / {@code text/tab-separated-values} content type and an {@code attachment}
 * disposition, so a browser will not render them — but without {@code nosniff} an aggressive browser
 * could still MIME-sniff a download as HTML and execute reflected markup. Setting the header globally
 * closes that gap for every response as defence-in-depth alongside the input allowlist in
 * {@code V1SearchController} (CodeQL java/xss, alert #2).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        filterChain.doFilter(request, response);
    }
}
