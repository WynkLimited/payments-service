package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
public class TokenizationConfig {
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
