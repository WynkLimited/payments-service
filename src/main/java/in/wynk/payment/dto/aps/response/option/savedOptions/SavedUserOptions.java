package in.wynk.payment.dto.aps.response.option.savedOptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SavedUserOptions {
    private String loginId;
    private String lobId;
    private List<AbstractSavedPayOptions> payOptions;
}
