package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class PaytmWalletSendOtpRequest {

    private String email;

    private String phone;

    private String clientId;

    private String scope;

    private String responseType;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public static interface EmailStep {
        PhoneStep withEmail(String email);
    }

    public static interface PhoneStep {
        ClientIdStep withPhone(String phone);
    }

    public static interface ClientIdStep {
        ScopeStep withClientId(String clientId);
    }

    public static interface ScopeStep {
        ResponseTypeStep withScope(String scope);
    }

    public static interface ResponseTypeStep {
        BuildStep withResponseType(String responseType);
    }

    public static interface BuildStep {
        PaytmWalletSendOtpRequest build();
    }

    public static class Builder
            implements EmailStep, PhoneStep, ClientIdStep, ScopeStep, ResponseTypeStep, BuildStep {
        private String email;
        private String phone;
        private String clientId;
        private String scope;
        private String responseType;

        private Builder() {
        }

        public static EmailStep paytmWalletSendOtpRequest() {
            return new Builder();
        }

        @Override
        public PhoneStep withEmail(String email) {
            this.email = email;
            return this;
        }

        @Override
        public ClientIdStep withPhone(String phone) {
            this.phone = phone;
            return this;
        }

        @Override
        public ScopeStep withClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        @Override
        public ResponseTypeStep withScope(String scope) {
            this.scope = scope;
            return this;
        }

        @Override
        public BuildStep withResponseType(String responseType) {
            this.responseType = responseType;
            return this;
        }

        @Override
        public PaytmWalletSendOtpRequest build() {
            return new PaytmWalletSendOtpRequest(
                    this.email,
                    this.phone,
                    this.clientId,
                    this.scope,
                    this.responseType
            );
        }
    }

    @Override
    public String toString() {
        return "PaytmWalletSendOtpRequest{" +
                "email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", clientId='" + clientId + '\'' +
                ", scope='" + scope + '\'' +
                ", responseType='" + responseType + '\'' +
                '}';
    }
}
