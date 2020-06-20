package in.wynk.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
