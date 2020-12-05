package in.wynk.payment.exception.handler;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.exception.handler.WynkGlobalExceptionHandler;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.net.URISyntaxException;

import static in.wynk.common.constant.BaseConstants.*;

@Slf4j
@ControllerAdvice
public class PaymentExceptionHandler extends WynkGlobalExceptionHandler {

    private final ConfigurableBeanFactory beanFactory;

    public PaymentExceptionHandler(ConfigurableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @ExceptionHandler(PaymentRuntimeException.class)
    public ResponseEntity<?> handlePaymentRuntimeException(PaymentRuntimeException ex, WebRequest request) throws URISyntaxException {
        PaymentErrorType errorType = PaymentErrorType.getWynkErrorType(ex.getErrorCode());
        if (errorType.getHttpResponseStatusCode() == HttpStatus.FOUND && errorType.getRedirectUrlProp() != null) {
            SessionDTO session = SessionContextHolder.getBody();
            String failureWebViewUrl = session.get(FAILURE_WEB_URL);
            if (StringUtils.isEmpty(failureWebViewUrl)) {
                final String sid = SessionContextHolder.getId();
                final String os = session.get(BaseConstants.OS);
                failureWebViewUrl = beanFactory.resolveEmbeddedValue(errorType.getRedirectUrlProp()) + sid + SLASH + os;
            } else {
                URIBuilder builder = new URIBuilder(failureWebViewUrl);
                if (TransactionContext.get() != null && StringUtils.isNotEmpty(TransactionContext.get().getIdStr())) {
                    builder.addParameter(TRANSACTION_ID, TransactionContext.get().getIdStr());
                }
                builder.addParameter(TRANSACTION_STATUS, TransactionContext.get().getStatus().getValue());
                failureWebViewUrl = builder.toString();
            }
            AnalyticService.update(RESPONSE_PAYLOAD, failureWebViewUrl);
            return BaseResponse.redirectResponse(failureWebViewUrl).getResponse();
        }
        return super.handleWynkRuntimeExceptionInternal(ex, request);
    }

}
