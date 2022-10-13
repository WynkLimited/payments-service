package in.wynk.payment.service;

import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;

public interface IMerchantIapPaymentVerificationService {

    BaseResponse<?> verifyReceipt(LatestReceiptResponse latestReceiptResponse);

    LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequest iapVerificationRequest);

    LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequestV2 iapVerificationRequest);

}
