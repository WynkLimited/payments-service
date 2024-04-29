package in.wynk.payment.dto.gpbs.response.receipt;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@AnalysedEntity
public abstract class AbstractGooglePlayReceiptVerificationResponse {
    private String kind;
    private String developerPayload;
    private String orderId;
    private String purchaseType; //0. Test (i.e. purchased from a license testing account) 1. Promo (i.e. purchased using a promo code) 2. Rewarded (i.e. from watching a video ad instead of paying)
    private Integer acknowledgementState;//0. Yet to be acknowledged 1. Acknowledged
    private String obfuscatedExternalAccountId;
    private String obfuscatedExternalProfileId;
}
