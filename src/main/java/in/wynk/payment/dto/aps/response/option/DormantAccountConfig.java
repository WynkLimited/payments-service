package in.wynk.payment.dto.aps.response.option;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class DormantAccountConfig {
    private String offertSubText;
    private String offerText;
    private String accountSubText;
    private String accountText;
}
