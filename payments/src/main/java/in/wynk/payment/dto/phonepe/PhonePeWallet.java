package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhonePeWallet {

    private Long usableBalance;
    private Long maxTopupAllowed;
    private Long availableBalance;
    private Boolean walletTopupSuggested;
    private Boolean walletActive;

}