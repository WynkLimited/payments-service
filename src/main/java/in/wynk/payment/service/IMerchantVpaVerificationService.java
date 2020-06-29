package in.wynk.payment.service;

import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.VpaVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantVpaVerificationService {

    <T> BaseResponse<T> verifyVpa(VpaVerificationRequest vpaVerificationRequest);
}
