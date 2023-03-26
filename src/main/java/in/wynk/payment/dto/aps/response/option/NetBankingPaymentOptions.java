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
public class NetBankingPaymentOptions {
    private List<NetBankingSubOptions> subOption;

    @Getter
    @SuperBuilder
    public static class NetBankingSubOptions {
        private String type;
        private String subType;
        private String name;
        private String health;
        private boolean recommended;
        private String iconUrl;
        private BigDecimal minAmount;
    }
}
