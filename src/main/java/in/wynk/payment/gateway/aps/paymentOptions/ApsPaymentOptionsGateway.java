package in.wynk.payment.gateway.aps.paymentOptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.aps.request.option.ApsPaymentOptionRequest;
import in.wynk.payment.dto.aps.response.option.ApsPaymentOptionsResponse;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.APS_PAYMENT_OPTIONS)
public class ApsPaymentOptionsGateway {

    @Value("${aps.payment.option.api}")
    private String PAYMENT_OPTION_ENDPOINT;

    private final ApsCommonGateway common;

    public ApsPaymentOptionsGateway (ApsCommonGateway common, ObjectMapper objectMapper) {
        this.common = common;
    }

    public ApsPaymentOptionsResponse payOption (String msisdn) {
        final ApsPaymentOptionRequest request = ApsPaymentOptionRequest.builder().build();
        return common.exchange(PAYMENT_OPTION_ENDPOINT, HttpMethod.POST, common.getLoginId(msisdn), request, ApsPaymentOptionsResponse.class);
    }
}

