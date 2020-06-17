package in.wynk.payment.service;

import in.wynk.payment.dto.request.SendOtpRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantOtpService {

    <T> BaseResponse<T> sendOtp(SendOtpRequest sendOtpRequest);

}
