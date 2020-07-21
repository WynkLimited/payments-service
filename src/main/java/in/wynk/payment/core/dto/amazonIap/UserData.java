package in.wynk.payment.core.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserData {
    private String userId;
    private String marketPlace;

}
