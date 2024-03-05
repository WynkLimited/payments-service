package in.wynk.payment.dto.gpbs.response.receipt;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.gpbs.response.receipt.IntroductoryPriceInfo;
import in.wynk.payment.dto.gpbs.response.receipt.SubscriptionCancelSurveyResult;
import in.wynk.payment.dto.gpbs.response.receipt.SubscriptionPriceChange;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@AnalysedEntity
public class GooglePlayReceiptResponse {
    private String kind;
    private String startTimeMillis;
    private String expiryTimeMillis;
    private String autoResumeTimeMillis;
    private boolean autoRenewing;
    private String priceCurrencyCode;
    private String priceAmountMicros;
    private IntroductoryPriceInfo introductoryPriceInfo;
    private String countryCode;
    private String developerPayload;
    private Integer paymentState; //payment state 2 means free trial
    private Integer cancelReason;
    private String userCancellationTimeMillis;
    private SubscriptionCancelSurveyResult cancelSurveyResult;
    private String orderId;
    private String linkedPurchaseToken;
    private String purchaseType;
    private SubscriptionPriceChange priceChange;
    private String profileName;
    private String emailAddress;
    private String givenName;
    private String familyName;
    private String profileId;
    private Integer acknowledgementState;
    private String externalAccountId;
    private Integer promotionType;
    private String promotionCode;
    private String obfuscatedExternalAccountId;
    private String obfuscatedExternalProfileId;
}
