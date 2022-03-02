package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public abstract class AbstractSavedDetail {
    private String isFavourite;
    private String isValid;
}
