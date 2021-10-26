package in.wynk.payment.dto;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class WinBackToken extends AbstractAuthenticationToken {

    private final String principal;
    private final String credentials;
    private final String transactionId;
    private final long ttl;

    public WinBackToken(String principal, String credentials, String transactionId,  long ttl, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        this.transactionId = transactionId;
        this.ttl = ttl;
    }

}
