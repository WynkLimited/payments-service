package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ConsultBalanceRequestHead {
    private String clientId;

    private String requestTimestamp;

    private String signature;

    private String version;

    private String channelId;

    @Override
    public String toString() {
        return "Head{" +
                "clientId='" + clientId + '\'' +
                ", requestTimestamp='" + requestTimestamp + '\'' +
                ", signature='" + signature + '\'' +
                ", version='" + version + '\'' +
                ", channelId='" + channelId + '\'' +
                '}';
    }

    public static interface ClientIdStep {
        RequestTimestampStep withClientId(String clientId);
    }

    public static interface RequestTimestampStep {
        SignatureStep withRequestTimestamp(String requestTimestamp);
    }

    public static interface SignatureStep {
        VersionStep withSignature(String signature);
    }

    public static interface VersionStep {
        ChannelIdStep withVersion(String version);
    }

    public static interface ChannelIdStep {
        BuildStep withChannelId(String channelId);
    }

    public static interface BuildStep {
        ConsultBalanceRequestHead build();
    }

    public static class Builder
            implements ClientIdStep, RequestTimestampStep, SignatureStep, VersionStep,
            ChannelIdStep, BuildStep {
        private String clientId;
        private String requestTimestamp;
        private String signature;
        private String version;
        private String channelId;

        private Builder() {
        }

        public static ClientIdStep consultBalanceWalletRequestHead() {
            return new Builder();
        }

        @Override
        public RequestTimestampStep withClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        @Override
        public SignatureStep withRequestTimestamp(String requestTimestamp) {
            this.requestTimestamp = requestTimestamp;
            return this;
        }

        @Override
        public VersionStep withSignature(String signature) {
            this.signature = signature;
            return this;
        }

        @Override
        public ChannelIdStep withVersion(String version) {
            this.version = version;
            return this;
        }

        @Override
        public BuildStep withChannelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        @Override
        public ConsultBalanceRequestHead build() {
            return new ConsultBalanceRequestHead(
                    this.clientId,
                    this.requestTimestamp,
                    this.signature,
                    this.version,
                    this.channelId
            );
        }
    }
}
