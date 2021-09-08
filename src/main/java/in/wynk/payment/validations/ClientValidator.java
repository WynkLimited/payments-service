package in.wynk.payment.validations;

import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;

import static in.wynk.client.core.constant.ClientErrorType.CLIENT004;

public class ClientValidator<T extends IClientValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        if (!request.getClientDetails().getService().equalsIgnoreCase(request.getService()))
            throw new WynkRuntimeException(CLIENT004);
        super.handle(request);
    }
}