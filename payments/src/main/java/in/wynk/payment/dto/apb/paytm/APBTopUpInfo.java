package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class APBTopUpInfo {
        private String wallet;
        private String paymentMode;
        private double topUpAmount;
        private String currency;
        private String walletLoginId;
        private APBPaytmRequestData data;
}
