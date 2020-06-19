package in.wynk.payment.dto.response;

import in.wynk.payment.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ItunesResponse {
        int                partnerProductId;

        int                productId;

        long               vtd;

        SubscriptionStatus status;

        @Override
        public String toString() {
            return "ItunesFinalResponse [partnerProductId=" + partnerProductId + ", productId=" + productId + ", vtd=" + vtd + ", status=" + status + "]";
        }
}
