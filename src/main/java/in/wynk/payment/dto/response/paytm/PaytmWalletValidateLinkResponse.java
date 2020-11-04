package in.wynk.payment.dto.response.paytm;

import in.wynk.common.enums.Status;
import in.wynk.payment.dto.response.CustomResponse;

public class PaytmWalletValidateLinkResponse extends CustomResponse {

    private String access_token;

    private long expires;

    private String scope;

    private String resourceOwnerId;

    public PaytmWalletValidateLinkResponse() {
    }

    public PaytmWalletValidateLinkResponse(Status status, String statusMessage, String responseCode,
                                           String statusCode, String message, String access_token, long expires,
                                           String scope, String resourceOwnerId) {
        super(status, statusMessage, responseCode, statusCode, message);
        this.access_token = access_token;
        this.expires = expires;
        this.scope = scope;
        this.resourceOwnerId = resourceOwnerId;
    }

    public String getAccess_token() {
        return access_token;
    }

    public long getExpires() {
        return expires;
    }

    public String getScope() {
        return scope;
    }

    public String getResourceOwnerId() {
        return resourceOwnerId;
    }

    public static interface StatusStep {
        StatusMessageStep withStatus(Status status);
    }

    public static interface StatusMessageStep {
        ResponseCodeStep withStatusMessage(String statusMessage);
    }

    public static interface ResponseCodeStep {
        StatusCodeStep withResponseCode(String responseCode);
    }

    public static interface StatusCodeStep {
        MessageStep withStatusCode(String statusCode);
    }

    public static interface MessageStep {
        Access_tokenStep withMessage(String message);
    }

    public static interface Access_tokenStep {
        ExpiresStep withAccess_token(String access_token);
    }

    public static interface ExpiresStep {
        ScopeStep withExpires(long expires);
    }

    public static interface ScopeStep {
        ResourceOwnerIdStep withScope(String scope);
    }

    public static interface ResourceOwnerIdStep {
        BuildStep withResourceOwnerId(String resourceOwnerId);
    }

    public static interface BuildStep {
        PaytmWalletValidateLinkResponse build();
    }

    public static class Builder
            implements StatusStep, StatusMessageStep, ResponseCodeStep, StatusCodeStep, MessageStep,
            Access_tokenStep, ExpiresStep, ScopeStep, ResourceOwnerIdStep, BuildStep {
        private Status status;
        private String statusMessage;
        private String responseCode;
        private String statusCode;
        private String message;
        private String access_token;
        private long expires;
        private String scope;
        private String resourceOwnerId;

        private Builder() {
        }

        public static StatusStep walletLinkResponse() {
            return new Builder();
        }

        @Override
        public StatusMessageStep withStatus(Status status) {
            this.status = status;
            return this;
        }

        @Override
        public ResponseCodeStep withStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        @Override
        public StatusCodeStep withResponseCode(String responseCode) {
            this.responseCode = responseCode;
            return this;
        }

        @Override
        public MessageStep withStatusCode(String statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        @Override
        public Access_tokenStep withMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public ExpiresStep withAccess_token(String access_token) {
            this.access_token = access_token;
            return this;
        }

        @Override
        public ScopeStep withExpires(long expires) {
            this.expires = expires;
            return this;
        }

        @Override
        public ResourceOwnerIdStep withScope(String scope) {
            this.scope = scope;
            return this;
        }

        @Override
        public BuildStep withResourceOwnerId(String resourceOwnerId) {
            this.resourceOwnerId = resourceOwnerId;
            return this;
        }

        @Override
        public PaytmWalletValidateLinkResponse build() {
            return new PaytmWalletValidateLinkResponse(
                    this.status,
                    this.statusMessage,
                    this.responseCode,
                    this.statusCode,
                    this.message,
                    this.access_token,
                    this.expires,
                    this.scope,
                    this.resourceOwnerId
            );
        }
    }

    @Override
    public String toString() {
        return "WalletLinkResponse{" +
                "access_token='" + access_token + '\'' +
                ", expires=" + expires +
                ", scope='" + scope + '\'' +
                ", resourceOwnerId='" + resourceOwnerId + '\'' +
                "} " + super.toString();
    }
}
