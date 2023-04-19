package in.wynk.payment.dto.common;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class UpiSavedInfo extends AbstractSavedInstrumentInfo {
    private String vpa;
    private String packageId;
}
