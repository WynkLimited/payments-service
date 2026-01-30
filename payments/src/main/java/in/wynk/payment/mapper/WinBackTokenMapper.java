package in.wynk.payment.mapper;

import in.wynk.auth.constant.AuthLoggingMarker;
import in.wynk.auth.exception.WynkAuthErrorType;
import in.wynk.auth.exception.WynkAuthenticationException;
import in.wynk.auth.mapper.AbstractPreAuthTokenMapper;
import in.wynk.payment.dto.WinBackToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
public class WinBackTokenMapper extends AbstractPreAuthTokenMapper {

    @Override
    public Authentication parse(HttpServletRequest request) throws WynkAuthenticationException {
        try {
            final String principal = new String(Base64.getDecoder().decode(URLDecoder.decode(request.getParameter(CLIENT_IDENTITY), "UTF-8")));
            final long ttl = Long.parseLong(URLDecoder.decode(request.getParameter(TTL), "UTF-8"));
            final String credentials = request.getParameter(TOKEN_ID).replace(" ", "+");
            final String[] splitter = request.getRequestURI().split(SLASH);
            final String transactionId = splitter[splitter.length - 1];
            return new WinBackToken(principal, credentials, transactionId, ttl, null);
        } catch (Exception e) {
            log.error(AuthLoggingMarker.AUTHENTICATION_FAILURE, "unable to parse validate the request", e);
            throw new WynkAuthenticationException(WynkAuthErrorType.AUTH007);
        }
    }

    @Override
    public void validate(Authentication authentication) throws WynkAuthenticationException {
        final WinBackToken token = (WinBackToken) authentication;
        if (StringUtils.isEmpty(token.getPrincipal()) || StringUtils.isEmpty(token.getCredentials()) || StringUtils.isEmpty(token.getTransactionId()) || token.getTtl() <= 0)
            throw new WynkAuthenticationException(WynkAuthErrorType.AUTH007);
        try {
            UUID.fromString(token.getTransactionId());
        } catch (Exception e) {
            throw new WynkAuthenticationException(WynkAuthErrorType.AUTH007);
        }
    }

}
