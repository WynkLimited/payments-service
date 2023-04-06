package in.wynk.payment.dto.common;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class UpiSavedInfo extends AbstractSavedInstrumentInfo {
    private String vpa;
    private String packageId;

    @Override
    public int hashCode() {
        return getVpa().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!UpiSavedInfo.class.isAssignableFrom(obj.getClass())) return false;
        final UpiSavedInfo savedInfo = ((UpiSavedInfo) obj);
        return savedInfo.getVpa().equals(this.getVpa());
    }
}
