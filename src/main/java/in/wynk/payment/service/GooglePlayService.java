package in.wynk.payment.service;

import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;

/**
 * @author Nishesh Pandey
 */
public interface GooglePlayService {

    void acknowledgeSubscription (String packageName, String service, String purchaseToken, String developerPayload, String skuId, boolean isAsync);
}
