package in.wynk.payment.dto.aps.response.option.paymentOptions;

import lombok.*;

import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DormantAccountConfig implements Serializable {
    private String offertSubText;
    private String offerText;
    private String accountSubText;
    private String accountText;
}
