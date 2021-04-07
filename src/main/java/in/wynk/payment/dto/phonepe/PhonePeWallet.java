package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PhonePeWallet {
    Long availableBalance;
    Long usableBalance;
    Long maxTopupAllowed;
    Boolean walletActive;
    Boolean walletTopupSuggested;
}
