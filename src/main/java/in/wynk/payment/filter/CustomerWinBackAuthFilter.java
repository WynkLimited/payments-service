package in.wynk.payment.filter;

import ch.qos.logback.core.net.SyslogOutputStream;
import in.wynk.auth.constant.AuthLoggingMarker;
import in.wynk.auth.exception.WynkAuthErrorType;
import in.wynk.auth.mapper.AbstractPreAuthTokenMapper;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
public class CustomerWinBackAuthFilter extends BasicAuthenticationFilter {

    private final String winBackUrl;
    private final AbstractPreAuthTokenMapper tokenMapper;
    private final AuthenticationManager authenticationManager;

    public CustomerWinBackAuthFilter(String winBackUrl, AuthenticationManager authenticationManager, AbstractPreAuthTokenMapper tokenMapper) {
        super(authenticationManager);
        this.winBackUrl = winBackUrl;
        this.tokenMapper = tokenMapper;
        this.authenticationManager = authenticationManager;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("inside CustomerWinBackAuthFilter ...");
        if (StringUtils.isNotEmpty(request.getParameter(CID)) && StringUtils.isNotEmpty(request.getParameter(TOKEN_ID))) {
            try {
                final Authentication preAuthDetails = tokenMapper.map(request);
                final Authentication authentication = this.authenticationManager.authenticate(preAuthDetails);
                if (authentication.isAuthenticated()) {
                    onSuccessfulAuthentication(request, response, authentication);
                } else {
                    response.sendRedirect(buildFailureUrl(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.timeout.page}"), request));
                }
            } catch (Exception e) {
                log.error(WynkAuthErrorType.AUTH007.getMarker(), "authentication is failed due to {}", e.getMessage(), e);
                response.sendRedirect(buildFailureUrl(EmbeddedPropertyResolver.resolveEmbeddedValue("${payment.timeout.page}"), request));
                return;
            }
        }
            chain.doFilter(request, response);
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) {
        log.debug(AuthLoggingMarker.AUTHENTICATION_SUCCESS, "authentication successful for principal-digest {}", authResult.getPrincipal());
        SecurityContextHolder.getContext().setAuthentication(authResult);
    }

    private String buildFailureUrl(String baseUrl, HttpServletRequest request) {
        return baseUrl + UUID.randomUUID().toString() + SLASH + decode(request, OS) + QUESTION_MARK + SERVICE + EQUAL + decode(request, SERVICE) + AND + APP_ID + EQUAL + decode(request, APP_ID) + AND + BUILD_NO + EQUAL + decode(request, BUILD_NO);
    }

    @SneakyThrows
    private String decode(HttpServletRequest request, String key) {
        return URLDecoder.decode(request.getParameter(key), "UTF-8");
    }

}
