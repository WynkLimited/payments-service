package in.wynk.payment.exception.handler;

import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.exception.handler.WynkGlobalExceptionHandler;
import in.wynk.payment.core.dto.response.BaseResponse;
import in.wynk.payment.core.enums.PaymentErrorType;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@ControllerAdvice
public class PaymentExceptionHandler extends WynkGlobalExceptionHandler {

    private final ConfigurableBeanFactory beanFactory;

    public PaymentExceptionHandler(ConfigurableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @ExceptionHandler(PaymentRuntimeException.class)
    public ResponseEntity<?> handlePaymentRuntimeException(PaymentRuntimeException ex, WebRequest request) {
        PaymentErrorType errorType = PaymentErrorType.getWynkErrorType(ex.getErrorCode());
        if (errorType.getHttpResponseStatusCode() == HttpStatus.FOUND && errorType.getRedirectUrlProp() != null) {
            final String sid = SessionContextHolder.getId();
            final String tid = SessionContextHolder.<SessionDTO>getBody().get(SessionKeys.WYNK_TRANSACTION_ID);
            final String webViewUrl = beanFactory.resolveEmbeddedValue(errorType.getRedirectUrlProp());
            return BaseResponse.redirectResponse(webViewUrl + sid + "/" + tid).getResponse();
        }
        return super.handleWynkRuntimeExceptionInternal(ex, request);
    }

}
