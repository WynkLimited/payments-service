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
    private Long availableBalance;
    private Long usableBalance;
    private Long maxTopupAllowed;
    private Boolean walletActive;
    private Boolean walletTopupSuggested;
}
