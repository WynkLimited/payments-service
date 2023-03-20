package in.wynk.payment.dto.payu.internal.charge.upi;

import in.wynk.payment.dto.common.IUpiIntentSpec;
import in.wynk.payment.dto.payu.PayUConstants;
import in.wynk.payment.dto.payu.external.charge.upi.intent.PayUUpiIntentExternalChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
public class PayUUpiIntentGatewayChargingResponse extends AbstractPayUUpiGatewayChargingResponse implements IUpiIntentSpec {

    private PayUUpiIntentExternalChargingResponse external;

    @Override
    public String getPayeeVpa() {
        return external.getResult().getMerchantVpa();
    }

    @Override
    public String getPayeeDisplayName() {
        return external.getResult().getMerchantName();
    }

    @Override
    public String getMerchantOrderID() {
        return external.getResult().getPaymentId();
    }

    @Override
    public String getMerchantCategoryCode() {
        return PayUConstants.PAYU_MERCHANT_CODE;
    }

    @Override
    public String getAmountToBePaid() {
        return external.getResult().getAmount();
    }

    @Override
    public Optional<String> getTransactionNote() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrencyCode() {
        return Optional.empty();
    }

}
