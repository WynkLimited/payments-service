package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
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
            return PaymentConstants.APS.concat("_").concat(NetBankingConstants.NET_BANKING).concat("_").concat(getSubType());
        }
    }
}
