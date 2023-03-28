package in.wynk.payment.dto.aps.response.option;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SavedUserOptions {
    private String loginId;
    private String lobId;
    private List<AbstractSavedPayOptions> payOptions;
}
