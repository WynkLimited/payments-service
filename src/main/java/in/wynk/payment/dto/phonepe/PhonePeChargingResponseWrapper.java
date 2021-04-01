package in.wynk.payment.dto.phonepe;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhonePeChargingResponseWrapper implements PhonePeResponse.PhonePeResponseWrapper {
   private String redirectType;
   private String redirectURL;
}
