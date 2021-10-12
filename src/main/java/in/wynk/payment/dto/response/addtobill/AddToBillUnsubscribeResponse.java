package in.wynk.payment.dto.response.addtobill;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;

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
    private Date chargeThroughDate;
    private Date subscriptionUpdateDate;
    private String unsubscriptionReason;
    private Date createDate;
    private Date renewalDate;
    private boolean isMarkedForCancel;
    private String chargingCycle;
    private long productPrice;
    private String lob;
    private Date periodStartDate;
    private boolean optin;
    private String provisionSi;
    private boolean waiverEligible;
    private boolean markedForCancel;

}
