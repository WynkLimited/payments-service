package in.wynk.payment.dto.aps.response.verify;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@Builder
public class ApsBinVerificationResponseData {
    private String cardBin;
    private String cardNetwork;
    private String cardCategory;
    private String bankCode;
    private String cvvLength;
    private String bankName;
    private boolean blocked;
    private String healthState;
    private boolean autoPayEnable;
    private String paymentMode;
    private boolean domestic;
    private TokenizationConfig tokenizationConfig;
    private String blockedReason;



    @Getter
    @Setter
    @Builder
    public static class TokenizationConfig {
        private String consentText;
        private String consentSubText;
        private String consentPopupHeader;
        private List<ConsentPopupTnC> consentPopupTnC;
        private String consentPopupButton;
        private boolean consentChecked;

        @Getter
        @Setter
        @Builder
        public static class ConsentPopupTnC{
            private String iconURL;
            private boolean gradient;
            private String text;
            private String subText;
        }
    }
}
