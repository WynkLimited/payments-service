package in.wynk.payment.provider;

import in.wynk.auth.constant.AuthLoggingMarker;
import in.wynk.auth.dao.entity.Client;
import in.wynk.auth.exception.WynkAuthErrorType;
import in.wynk.auth.exception.WynkAuthenticationException;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.payment.dto.WinBackToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class WinBackAuthenticationProvider implements AuthenticationProvider {

    private final ClientDetailsCachingService clientDetailsCache;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.debug("authenticating churned user");
        final WinBackToken preAuthToken = (WinBackToken) authentication;
        final Client client = clientDetailsCache.getClientByAlias(authentication.getPrincipal().toString());
        final String droppedTransactionId = preAuthToken.getTransactionId();
        if (preAuthToken.getTtl()  >  System.currentTimeMillis()) {
            final String signedToken = EncryptionUtils.generateAppToken(droppedTransactionId + BaseConstants.COLON + preAuthToken.getTtl(), client.getClientSecret());
            final Authentication authenticated = new WinBackToken(client.getClientId(), signedToken, droppedTransactionId, preAuthToken.getTtl(), Collections.singleton(new SimpleGrantedAuthority("READ_ONLY")));
            if (signedToken.equals(preAuthToken.getCredentials())) {
                authenticated.setAuthenticated(true);
            } else {
                log.info(AuthLoggingMarker.AUTHENTICATION_FAILURE, "Request Signature: {} does not match with computed signature: {}", authentication.getCredentials(), preAuthToken.getCredentials());
                throw new WynkAuthenticationException(WynkAuthErrorType.AUTH006);
            }
            return authenticated;
        } else {
            return new WinBackToken(preAuthToken.getPrincipal(), preAuthToken.getCredentials(), droppedTransactionId, preAuthToken.getTtl(), null);
        }
    }

    @Override
    public boolean supports(Class<?> target) {
        return WinBackToken.class.isAssignableFrom(target);
    }
}
