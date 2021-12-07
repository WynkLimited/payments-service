package in.wynk.payment.dto.response.addtobill;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddToBillUnsubscribeResponse {
    private String si;
    private String productCode;
    private String chargingPrice;
    private String subProductCode;
    private String unsubscriptionReason;
    private boolean isIsMarkedForCancel;
    private String chargingCycle;
    private long productPrice;
    private String lob;
    private boolean optin;
    private String provisionSi;
    private boolean waiverEligible;
    private boolean markedForCancel;

    public boolean isIsMarkedForCancel() {
        return isIsMarkedForCancel;
    }

    public void setIsMarkedForCancel(boolean isMarkedForCancel) {
        isIsMarkedForCancel = isMarkedForCancel;
    }

    public boolean isMarkedForCancel() {
        return markedForCancel;
    }

    public void setMarkedForCancel(boolean markedForCancel) {
        this.markedForCancel = markedForCancel;
    }
}