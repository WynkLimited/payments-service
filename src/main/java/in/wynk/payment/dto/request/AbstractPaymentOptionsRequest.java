package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.dto.IPaymentOptionsRequest;
import in.wynk.payment.validations.IProductValidatorRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class AbstractPaymentOptionsRequest <T extends IPaymentOptionsRequest> implements IProductValidatorRequest {
    @Analysed
    private T paymentOptionRequest;

    @Override
    public boolean isTrialOpted () {
        return false;
    }

    @Override
    public boolean isAutoRenewOpted () {
        return Objects.nonNull(paymentOptionRequest.getPaymentDetails()) && paymentOptionRequest.getPaymentDetails().isAutoRenew();
    }

    @Override
    public IAppDetails getAppDetails () {
        return paymentOptionRequest.getAppDetails();
    }

    @Override
    public IUserDetails getUserDetails () {
        return paymentOptionRequest.getUserDetails();
    }

    @Override
    public IProductDetails getProductDetails () {
        return paymentOptionRequest.getProductDetails();
    }

    @Override
    public IPaymentDetails getPaymentDetails () {
        return paymentOptionRequest.getPaymentDetails();
    }
}
