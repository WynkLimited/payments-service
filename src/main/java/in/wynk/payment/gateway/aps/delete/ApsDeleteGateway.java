package in.wynk.payment.gateway.aps.delete;

import in.wynk.common.dto.SessionDTO;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.common.DeleteType;
import in.wynk.payment.dto.aps.request.delete.DeleteCardRequest;
import in.wynk.payment.dto.aps.request.delete.DeleteVpaRequest;
import in.wynk.payment.dto.common.response.AbstractPaymentMethodDeleteResponse;
import in.wynk.payment.dto.gateway.delete.DeleteCardResponse;
import in.wynk.payment.dto.gateway.delete.DeleteVpaResponse;
import in.wynk.payment.dto.request.PaymentMethodDeleteRequest;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.service.IPaymentDeleteService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_SAVED_CARD_DELETION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_SAVED_VPA_DELETION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.APS_DELETE)
public class ApsDeleteGateway implements IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest> {

    @Value("${aps.payment.delete.card}")
    private String DELETE_CARD_ENDPOINT;
    @Value("${aps.payment.delete.vpa}")
    private String DELETE_VPA_ENDPOINT;

    private final ApsCommonGateway common;
    private final PaymentMethodDeletion verification = new PaymentMethodDeletion();

    public ApsDeleteGateway(ApsCommonGateway common) {
        this.common = common;
    }

    @Override
    public AbstractPaymentMethodDeleteResponse delete (PaymentMethodDeleteRequest request) {
        return verification.delete(request);
    }

    private class PaymentMethodDeletion implements IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest> {
        private final Map<DeleteType, IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest>> deletionDelegate = new HashMap<>();

        public PaymentMethodDeletion () {
            deletionDelegate.put(DeleteType.VPA, new VpaDeletion());
            deletionDelegate.put(DeleteType.CARD, new CardDeletion());//Card verification
        }

        @Override
        public AbstractPaymentMethodDeleteResponse delete (PaymentMethodDeleteRequest request) {
            return deletionDelegate.get(request.getDeleteType()).delete(request);
        }

        private class CardDeletion implements IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest> {
            @Override
            public AbstractPaymentMethodDeleteResponse delete (PaymentMethodDeleteRequest request) {
                final DeleteCardRequest deleteCardRequest = DeleteCardRequest.builder().referenceNumber(request.getDeleteValue()).build();
                final SessionDTO sessionDTO = SessionContextHolder.getBody();
                try {
                    Boolean response = common.exchange(DELETE_CARD_ENDPOINT, HttpMethod.POST, common.getLoginId(sessionDTO.get("msisdn")), deleteCardRequest, Boolean.class);
                    return DeleteCardResponse.builder().deleted(response).build();
                } catch (Exception e) {
                    log.error(APS_SAVED_CARD_DELETION, "Card deletion failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY042, e);
                }
            }
        }

        private class VpaDeletion implements IPaymentDeleteService<AbstractPaymentMethodDeleteResponse, PaymentMethodDeleteRequest> {
            @Override
            public AbstractPaymentMethodDeleteResponse delete (PaymentMethodDeleteRequest request) {
                List<String> vpa= new ArrayList<>();
                vpa.add(request.getDeleteValue());
                final DeleteVpaRequest deleteVpaRequest = DeleteVpaRequest.builder().vpaIds(vpa).build();
                final SessionDTO sessionDTO = SessionContextHolder.getBody();
                try {
                    Boolean response = common.exchange(DELETE_VPA_ENDPOINT, HttpMethod.POST, common.getLoginId(sessionDTO.get("msisdn")), deleteVpaRequest, Boolean.class);
                    return DeleteVpaResponse.builder().deleted(response).build();
                } catch (Exception e) {
                    log.error(APS_SAVED_VPA_DELETION, "Vpa deletion failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY043, e);
                }
            }
        }
    }
}
