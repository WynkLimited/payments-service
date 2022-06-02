package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class VpaDetail extends AbstractSavedDetail {
    private String vpaAddress;
}
