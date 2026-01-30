package in.wynk.payment.dto.request;

import in.wynk.payment.dto.response.UiDetails;
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
    private boolean enabled;
    private UiDetails uiDetails;
}
