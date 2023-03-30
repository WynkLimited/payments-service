package in.wynk.payment.dto.aps.response.option;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
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
public class UpiSavedOptions extends AbstractSavedPayOptions {
    private String userVPA;
    private String upiApp;
    private String upiPspAppName;
    private String androidCustomisationString;
    private String iosCustomisationString;
    private BigDecimal walletBalance;
    private String displayName;
    private boolean enable;
    private String hyperSdkPackageName;
    private String upiFlow;
    private List<String> vpaIds;
    private List<String> disabledLobs;
    private boolean valid;
}
