package in.wynk.payment.dto.aps.common;

import lombok.*;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationConfig {
    private String consentText;
    private String consentSubText;
    private String consentPopupHeader;
    private List<ConsentPopupTnC> consentPopupTnC;
    private String consentPopupButton;
    private boolean consentChecked;

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentPopupTnC{
        private String iconURL;
        private boolean gradient;
        private String text;
        private String subText;
    }
}
