package in.wynk.payment.dto.common;

import in.wynk.vas.client.dto.atb.LinkedSis;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
public class BillingSavedInfo extends AbstractSavedInstrumentInfo {
    private final List<LinkedSis> linkedSis;
}
