package in.wynk.payment.dto.aps.response.option;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class UpiPaymentOptions {
    private String health;
    private List<UpiSupportedApps> upiSupportedApps;

    @Getter
    @SuperBuilder
    public static class UpiSupportedApps{
        private String upiPspAppName;
        private String androidCustomisationString;
        private String iosCustomisationString;
        private boolean enable;
        private Integer order;
        private String hyperSdkPackageName;
        private String iconUrl;
        private String health;
        private List<String> disabledLobs;
    }
}
