package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConsultBalanceRequestBody {
    private String userToken;

    private BigDecimal totalAmount;

    private String mid;

    private Map<String, BigDecimal> amountDetails;

    @Override
    public String toString() {
        return "Body{" +
                "userToken='" + userToken + '\'' +
                ", totalAmount=" + totalAmount +
                ", mid='" + mid + '\'' +
                ", amountDetails=" + amountDetails +
                '}';
    }

    public static interface UserTokenStep {
        TotalAmountStep withUserToken(String userToken);
    }

    public static interface TotalAmountStep {
        MidStep withTotalAmount(BigDecimal totalAmount);
    }

    public static interface MidStep {
        AmountDetailsStep withMid(String mid);
    }

    public static interface AmountDetailsStep {
        BuildStep withAmountDetails(Map<String, BigDecimal> amountDetails);
    }

    public static interface BuildStep {
        ConsultBalanceRequestBody build();
    }

    public static class Builder
            implements UserTokenStep, TotalAmountStep, MidStep, AmountDetailsStep, BuildStep {
        private String userToken;
        private BigDecimal totalAmount;
        private String mid;
        private Map<String, BigDecimal> amountDetails;

        private Builder() {
        }

        public static UserTokenStep consultBalanceWalletRequestBody() {
            return new Builder();
        }

        @Override
        public TotalAmountStep withUserToken(String userToken) {
            this.userToken = userToken;
            return this;
        }

        @Override
        public MidStep withTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        @Override
        public AmountDetailsStep withMid(String mid) {
            this.mid = mid;
            return this;
        }

        @Override
        public BuildStep withAmountDetails(Map<String, BigDecimal> amountDetails) {
            this.amountDetails = amountDetails;
            return this;
        }

        @Override
        public ConsultBalanceRequestBody build() {
            return new ConsultBalanceRequestBody(
                    this.userToken,
                    this.totalAmount,
                    this.mid,
                    this.amountDetails
            );
        }
    }
}
