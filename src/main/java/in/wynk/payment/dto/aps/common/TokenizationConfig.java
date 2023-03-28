package in.wynk.payment.dto.aps.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentPopupTnC{
        private String iconURL;
        private boolean gradient;
        private String text;
        private String subText;
    }
}
