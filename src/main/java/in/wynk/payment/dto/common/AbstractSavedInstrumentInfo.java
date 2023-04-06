package in.wynk.payment.dto.common;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AbstractSavedInstrumentInfo {
    private String type;
    private String id;
    private String code;
    private String group;
    private String title;
    private String iconUrl;
    private String health;
    private Integer order;
    private boolean valid;
    private boolean enable;
    private boolean preferred;
    private boolean favourite;
    private boolean recommended;
    private boolean autoPayEnabled;
    private boolean expressCheckout;
}
