package in.wynk.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseDetailsDto {

    private UserDetailsDto userDetails;
    private ProductDetailsDto productDetails;
    private PaymentDetailsDto paymentDetails;
    private AppDetailsDto appDetails;

}
