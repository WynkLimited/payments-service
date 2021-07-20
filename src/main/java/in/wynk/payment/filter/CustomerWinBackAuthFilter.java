package in.wynk.payment.filter;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomerWinBackAuthFilter extends BasicAuthenticationFilter {


    public CustomerWinBackAuthFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }
}
