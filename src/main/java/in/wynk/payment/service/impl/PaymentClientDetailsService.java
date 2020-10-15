package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.auth.service.IClientDetailsService;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

import static in.wynk.auth.constant.AuthConstant.S2S_READ_ACCESS_ROLE;
import static in.wynk.auth.constant.AuthConstant.S2S_WRITE_ACCESS_ROLE;

@Deprecated
public class PaymentClientDetailsService implements IClientDetailsService<Client> {

    private final Map<String, PaymentClient> clientsMap = new HashMap<>();

    public PaymentClientDetailsService() {
        loadClientDetails().forEach(client -> clientsMap.put(client.getClientId(), client));
    }


    @Override
    public Optional<Client> getClientDetails(String clientId) {
        return Optional.of(clientsMap.get(clientId));
    }

    private List<PaymentClient> loadClientDetails() {
        PaymentClient artistLiveClient = PaymentClient.builder().id("5b1b4d3d53325e49352cfd69389b4b3b").secret("893f3bc1e0396c01fddeb9b3459d04fe").authorities(Arrays.asList(S2S_READ_ACCESS_ROLE, S2S_WRITE_ACCESS_ROLE)).build();
        PaymentClient subscriptionClient = PaymentClient.builder().id("340b596b576da4fc73b21f852d1390b1").secret("e9a467c8a8ce787a0787901b137ef70a").authorities(Arrays.asList(S2S_READ_ACCESS_ROLE, S2S_WRITE_ACCESS_ROLE)).build();
        return Arrays.asList(artistLiveClient, subscriptionClient);
    }

    @Builder
    @Getter
    public static class PaymentClient implements Client {

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
