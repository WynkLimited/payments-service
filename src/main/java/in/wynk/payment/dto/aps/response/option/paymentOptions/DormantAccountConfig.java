package in.wynk.payment.dto.aps.response.option.paymentOptions;

import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DormantAccountConfig {
    private String offertSubText;
    private String offerText;
    private String accountSubText;
    private String accountText;
}
