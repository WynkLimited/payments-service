package in.wynk.payment.dto.aps.response.option;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class SavedUserOptions {
    private String loginId;
    private String lobId;
    private List<SavedPayOptions> payOptions;
}
