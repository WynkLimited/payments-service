package in.wynk.payment.dto.request;

import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOptionsRequest {
     UserDetails userDetails;
     AppDetails appDetails;
     String sid;
     String planId;
     String itemId;
     String couponId;
     String countryCode;
}
