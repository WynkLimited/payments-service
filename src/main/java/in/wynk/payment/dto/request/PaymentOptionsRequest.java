package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOptionsRequest {
     IUserDetails userDetails;
     IAppDetails appDetails;
     String sid;
     String planId;
     String itemId;
     String couponId;
     String countryCode;
}
