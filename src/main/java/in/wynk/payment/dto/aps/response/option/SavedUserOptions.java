package in.wynk.payment.dto.aps.response.option;

import lombok.*;
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
