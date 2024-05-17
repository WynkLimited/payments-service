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
public class GooglePlaySubscriptionReceiptResponse extends AbstractGooglePlayReceiptVerificationResponse {
    private String startTimeMillis;
    private String expiryTimeMillis;
    private String autoResumeTimeMillis;
    private boolean autoRenewing;
    private String priceCurrencyCode;
    private String priceAmountMicros;
    private IntroductoryPriceInfo introductoryPriceInfo;
    private String countryCode;
    private Integer paymentState; //payment state 2 means free trial
    private Integer cancelReason;
    private String userCancellationTimeMillis;
    private SubscriptionCancelSurveyResult cancelSurveyResult;
    private String linkedPurchaseToken;
    private SubscriptionPriceChange priceChange;
    private String profileName;
    private String emailAddress;
    private String givenName;
    private String familyName;
    private String profileId;
    private String externalAccountId;
    private Integer promotionType;
    private String promotionCode;
}
