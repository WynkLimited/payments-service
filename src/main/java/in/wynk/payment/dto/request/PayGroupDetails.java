package in.wynk.payment.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class PayGroupDetails {
    private String id;
    private String title;
    private String description;
    private PromotionDetails promotionDetails;
    private boolean enabled;
    private UIDetails uiDetails;
}
