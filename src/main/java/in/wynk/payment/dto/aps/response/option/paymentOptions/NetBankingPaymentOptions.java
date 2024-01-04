package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.NetBankingConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
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
public class NetBankingPaymentOptions extends AbstractPaymentOptions implements Serializable {
    private List<NetBankingSubOptions> subOption;

    @Override
    public List<NetBankingSubOptions> getOption() {
        return getSubOption();
    }

    @Getter
    @ToString
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetBankingSubOptions implements ISubOption, Serializable {
        private String type;
        private String subType;
        private String name;
        private String health;
        private boolean recommended;
        private String iconUrl;
        private BigDecimal minAmount;

        @Override
        public String getId() {
            return APS.concat("_").concat(NetBankingConstants.NET_BANKING).concat("_").concat(getSubType());
        }
    }
}
