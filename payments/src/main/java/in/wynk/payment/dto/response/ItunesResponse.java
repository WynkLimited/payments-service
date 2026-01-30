package in.wynk.payment.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class ItunesResponse {
        int   partnerProductId;

        int   productId;

        long  vtd;

        String status;

        String errorMsg;

        @Override
        public String toString() {
            return "ItunesFinalResponse [partnerProductId=" + partnerProductId + ", productId=" + productId + ", vtd=" + vtd + ", status=" + status + ", errorMsg=" + errorMsg + "]";
        }
}
