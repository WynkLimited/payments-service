package in.wynk.payment.dto.aps.response.option;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class UpiSavedOptions {
    private String userVPA;
    private String upiApp;
    private String upiPspAppName;
    private String androidCustomisationString;
    private String iosCustomisationString;
    private BigDecimal walletBalance;
    private String displayName;
    private boolean enable;
    private String hyperSdkPackageName;
    private List<String> disabledLobs;
}
