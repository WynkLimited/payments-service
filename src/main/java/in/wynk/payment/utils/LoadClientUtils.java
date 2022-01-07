package in.wynk.payment.utils;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

public class LoadClientUtils {

    public static void loadClient(boolean isS2S) {
        if (isS2S) loadClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        else loadClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT));
    }

    @ClientAware(clientId = "#clientId")
    private static void loadClientById(String clientId) {
    }

    @ClientAware(clientAlias = "#clientAlias")
    private static void loadClientByAlias(String clientAlias) {
    }

}