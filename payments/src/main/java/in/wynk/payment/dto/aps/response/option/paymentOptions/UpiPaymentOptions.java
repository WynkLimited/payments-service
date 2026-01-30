package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.UpiConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;

import static in.wynk.payment.dto.aps.common.ApsConstant.APS;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpiPaymentOptions extends AbstractPaymentOptions implements Serializable {
    private String health;
    private List<UpiSubOption> upiSupportedApps;

    @Override
    public List<UpiSubOption> getOption() {
        return getUpiSupportedApps();
    }

    @Getter
    @ToString
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpiSubOption implements ISubOption, Serializable {
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
        @Override
        public String getId() {
            return APS.concat("_").concat(UpiConstants.UPI).concat("_").concat(getUpiPspAppName());
        }

        @Override
        public boolean isEnabled() {
            return isEnable();
        }
    }
}
