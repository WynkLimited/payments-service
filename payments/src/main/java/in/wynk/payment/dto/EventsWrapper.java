package in.wynk.payment.dto;

import in.wynk.common.dto.IGeoLocation;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.text.SimpleDateFormat;
import java.util.Date;

@Builder
@Setter
@Getter
public class EventsWrapper {
    private String uid;
    //app Details
    private String os;
    private String deviceId;
    private Integer buildNo;
    private String deviceType;
    private String appVersion;
    private String appId;
    private String service;
    //user details
    private String msisdn;
    private String subscriberId;
    //payment details
    private String couponId;
    private String paymentId;
    private String paymentMode;
    private String merchantName;
    private Boolean isTrialOpted;
    private Boolean isAutoRenew;
    //product details
    private Integer planId;
    private String itemId;
    private String type;
    // transaction Details
    private String transactionId;
    private Double amount;
    private Double discount;
    private String initTime;
    private String clientAlias;
    private String item;
    private String paymentCode;
    private String paymentEvent;
    private String transactionStatus;
    private String coupon;
    private String exitTime;
    private String consentTime;

    private IAppDetails appDetails;
    private IUserDetails userDetails;
    private IPaymentDetails paymentDetails;
    private IProductDetails productDetails;
    private IGeoLocation geolocation;
    private Transaction transaction;
    private CallbackRequest callbackRequest;
    private PaymentReconciliationMessage paymentReconciliationMessage;
    private String extTxnId;

    private Boolean optForAutoRenew;
    private String triggerDate;

    public static String getTriggerDate() {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        return dateFormat.format(new Date());
    }
}
