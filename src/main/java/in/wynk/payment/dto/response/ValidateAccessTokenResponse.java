package in.wynk.payment.dto.response;

import in.wynk.payment.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ValidateAccessTokenResponse extends CustomResponse{
    private String id;

    private String email;

    private String mobile;

    private long expires;

    public ValidateAccessTokenResponse(Status status, String statusMessage, String responseCode,
                    String statusCode, String message, String id, String email, String mobile, long expires) {
        super(status, statusMessage, responseCode, statusCode, message);
        this.id = id;
        this.email = email;
        this.mobile = mobile;
        this.expires = expires;
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
        IdStep withMessage(String message);
    }

    public static interface IdStep {
        EmailStep withId(String id);
    }

    public static interface EmailStep {
        MobileStep withEmail(String email);
    }

    public static interface MobileStep {
        ExpiresStep withMobile(String mobile);
    }

    public static interface ExpiresStep {
        BuildStep withExpires(long expires);
    }

    public static interface BuildStep {
        ValidateAccessTokenResponse build();
    }

    public static class Builder
            implements StatusStep, StatusMessageStep, ResponseCodeStep, StatusCodeStep, MessageStep,
            IdStep, EmailStep, MobileStep, ExpiresStep, BuildStep {
        private Status status;
        private String statusMessage;
        private String responseCode;
        private String statusCode;
        private String message;
        private String id;
        private String email;
        private String mobile;
        private long expires;

        private Builder() {
        }

        public static StatusStep validateAccessTokenResponse() {
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
        public IdStep withMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public EmailStep withId(String id) {
            this.id = id;
            return this;
        }

        @Override
        public MobileStep withEmail(String email) {
            this.email = email;
            return this;
        }

        @Override
        public ExpiresStep withMobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        @Override
        public BuildStep withExpires(long expires) {
            this.expires = expires;
            return this;
        }

        @Override
        public ValidateAccessTokenResponse build() {
            return new ValidateAccessTokenResponse(
                    this.status,
                    this.statusMessage,
                    this.responseCode,
                    this.statusCode,
                    this.message,
                    this.id,
                    this.email,
                    this.mobile,
                    this.expires
            );
        }
    }

    @Override
    public String toString() {
        return "ValidateAccessTokenResponse{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", mobile='" + mobile + '\'' +
                ", expires=" + expires +
                "} " + super.toString();
    }
}
