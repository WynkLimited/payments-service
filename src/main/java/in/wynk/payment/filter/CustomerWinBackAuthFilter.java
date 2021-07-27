package in.wynk.payment.filter;

import in.wynk.auth.constant.AuthLoggingMarker;
import in.wynk.auth.mapper.AbstractPreAuthTokenMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        if (StringUtils.isNotEmpty(request.getRequestURL().toString()) && request.getRequestURL().toString().contains(winBackUrl)) {
            log.info("CustomerWinBackAuthFilter is applied to {} for {}...",request.getRequestURL().toString(), winBackUrl);
            final Authentication preAuthDetails = tokenMapper.map(request);
            Authentication authentication = this.authenticationManager.authenticate(preAuthDetails);
            if (authentication.isAuthenticated()) {
                onSuccessfulAuthentication(request, response, authentication);
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) {
        log.debug(AuthLoggingMarker.AUTHENTICATION_SUCCESS, "authentication successful for principal-digest {}", authResult.getPrincipal());
        SecurityContextHolder.getContext().setAuthentication(authResult);
    }

}
