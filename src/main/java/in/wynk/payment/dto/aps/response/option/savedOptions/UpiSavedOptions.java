package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.UpiConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

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
public class UpiSavedOptions extends AbstractSavedPayOptions implements Serializable {

    private String upiApp;
    private String userVPA;
    private String upiFlow;
    private String displayName;
    private String upiPspAppName;
    private String hyperSdkPackageName;
    private String androidCustomisationString;
    private String iosCustomisationString;

    private boolean valid;
    private boolean enable;

    private List<String> vpaIds;
    private List<String> disabledLobs;

    public String getUpiPspAppName() {
        if (StringUtils.isEmpty(upiPspAppName)) return UpiConstants.DEFAULT_COLLECT;
        return upiPspAppName;
    }
    public String getId() {
        return APS.concat("_").concat(UpiConstants.UPI).concat("_").concat(getUpiPspAppName());
    }
}
