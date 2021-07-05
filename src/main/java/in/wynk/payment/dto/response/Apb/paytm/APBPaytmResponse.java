package in.wynk.payment.dto.response.Apb.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APBPaytmResponse {
    private String errorCode;
    private boolean result;
    private APBPaytmResponseData data;
}
