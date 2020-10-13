package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.ClientDetails;
import in.wynk.auth.service.IClientDetailsService;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

import static in.wynk.auth.constant.AuthConstant.S2S_READ_ACCESS_ROLE;
import static in.wynk.auth.constant.AuthConstant.S2S_WRITE_ACCESS_ROLE;

public class PaymentClientDetailsService implements IClientDetailsService<ClientDetails> {

    private final Map<String, PaymentClientDetails> clientsMap = new HashMap<>();

    public PaymentClientDetailsService() {
        loadClientDetails().forEach(client -> clientsMap.put(client.getClientId(), client));
    }


    @Override
    public Optional<ClientDetails> getClientDetails(String clientId) {
        return Optional.of(clientsMap.get(clientId));
    }

    private List<PaymentClientDetails> loadClientDetails() {
        PaymentClientDetails artistLiveClient = PaymentClientDetails.builder().id("5b1b4d3d53325e49352cfd69389b4b3b").secret("893f3bc1e0396c01fddeb9b3459d04fe").authorities(Arrays.asList(S2S_READ_ACCESS_ROLE, S2S_WRITE_ACCESS_ROLE)).build();
        PaymentClientDetails subscriptionClient = PaymentClientDetails.builder().id("340b596b576da4fc73b21f852d1390b1").secret("e9a467c8a8ce787a0787901b137ef70a").authorities(Arrays.asList(S2S_READ_ACCESS_ROLE, S2S_WRITE_ACCESS_ROLE)).build();
        return Arrays.asList(artistLiveClient, subscriptionClient);
    }

    @Builder
    @Getter
    public static class PaymentClientDetails implements ClientDetails {

        private final String id;
        private final String secret;
        private final List<String> authorities;

        @Override
        public String getClientId() {
            return id;
        }

        @Override
        public String getClientSecret() {
            return secret;
        }

        @Override
        public List<String> getAuthorities() {
            return authorities;
        }
    }
}
