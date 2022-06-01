package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public abstract class AbstractPaymentOption {
    private String name;
    private long order;
    private boolean usePcidss;
}
