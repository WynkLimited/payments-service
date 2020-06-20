package in.wynk.payment.service.impl;

import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.utils.common.PaymentRequestType;
import in.wynk.utils.constant.PackType;
import in.wynk.utils.domain.SubscriptionPack;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static in.wynk.payment.constant.PaymentConstants.PIPE_SEPARATOR;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayuMerchantPaymentService implements IRenewalMerchantPaymentService {

  private String payUMerchantKey;

  private String payUSalt;

  @Override
  public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
    return null;
  }

  @Override
  public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
    String isICICI = StringUtils.EMPTY; // Keeping this empty for future use.
    SubscriptionPack pack = new SubscriptionPack(); // Get pack from partnerProductId.
    if (pack.getPackageType().equals(PackType.ONE_TIME_SUBSCRIPTION)) {
      startPaymentForPayU(pack, chargingRequest);
    }
    return null;
  }

  @Override
  public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
    return null;
  }

  @Override
  public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
    return null;
  }

  private void startPaymentForPayU(SubscriptionPack pack, ChargingRequest chargingRequest) {
    String firstName = PaymentConstants.DEFAULT_FIRST_NAME;
    String email = StringUtils.EMPTY;
    String uid = ""; // uid from msisdn.
    /*
    if (!WynkService.NOT_WCF_INTEGRATED_SERVICES.contains(chargingRequest.getService()))
        UserProfileData user = userDAO.getUserByUid(uid); // for name and email
    */
    String udf1 = StringUtils.EMPTY;
    String reqType = PaymentRequestType.DEFAULT.name();
    if (!pack.getPackageType().equals(PackType.ONE_TIME_SUBSCRIPTION)) {
      reqType = PaymentRequestType.SUBSCRIBE.name();
      udf1 = "SI";
    }
    String checksumHash = getChecksumHashForPayment(pack, chargingRequest, udf1, email, firstName);
  }

  private String getChecksumHashForPayment(
      SubscriptionPack pack,
      ChargingRequest chargingRequest,
      String udf1,
      String email,
      String firstName) {
    double amount = pack.getPricing().getRoundedPriceInRupees();
    String productTitle = pack.getTitle();
    String finalString =
        payUMerchantKey
            + PIPE_SEPARATOR
            + chargingRequest.getTransactionId()
            + PIPE_SEPARATOR
            + String.valueOf(amount)
            + PIPE_SEPARATOR
            + productTitle
            + PIPE_SEPARATOR
            + firstName
            + PIPE_SEPARATOR
            + email
            + PIPE_SEPARATOR
            + udf1
            + "||||||||||"
            + payUSalt;
    return null;
  }
}
