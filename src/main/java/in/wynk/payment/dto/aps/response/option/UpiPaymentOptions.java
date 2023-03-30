package in.wynk.payment.dto.aps.response.option;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class UpiPaymentOptions extends AbstractPaymentOptions {
    private String health;
    private List<UpiSupportedApps> upiSupportedApps;

    @Getter
    @ToString
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpiSupportedApps{
        private String upiPspAppName;
        private String androidCustomisationString;
        private String iosCustomisationString;
        private String displayName;
        private boolean enable;
        private Integer order;
        private String hyperSdkPackageName;
        private String iconUrl;
        private String health;
        private List<String> disabledLobs;
    }
}
