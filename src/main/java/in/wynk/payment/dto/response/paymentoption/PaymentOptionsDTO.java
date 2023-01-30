package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.PaymentGroupsDTO;
import in.wynk.payment.dto.response.billing.Billing;
import in.wynk.payment.dto.response.card.Card;
import in.wynk.payment.dto.response.netbanking.NetBanking;
import in.wynk.payment.dto.response.upi.UPI;
import in.wynk.payment.dto.response.wallet.Wallet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@SuperBuilder
@Getter
@AnalysedEntity
public class PaymentOptionsDTO {
    @JsonProperty("pay_group_details")
    private final List<PaymentGroupsDTO> paymentGroups;
    @JsonProperty("payment_method_details")
    private final List<PaymentMethodDTO> paymentMethods;

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class PaymentMethodDTO {
        @JsonProperty("UPI")
        private List<UPI> upi;

        @JsonProperty("CARD")
        private List<Card> card;

        @JsonProperty("WALLET")
        private List<Wallet> wallet;

        @JsonProperty("NET_BANKING")
        private List<NetBanking> netBanking;

        @JsonProperty("BILLING")
        private List<Billing> billing;
    }
}
