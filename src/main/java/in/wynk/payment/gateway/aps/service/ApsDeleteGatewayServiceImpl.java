package in.wynk.payment.gateway.aps.service;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.common.DeleteType;
import in.wynk.payment.dto.aps.request.delete.DeleteCardRequest;
import in.wynk.payment.dto.aps.request.delete.DeleteVpaRequest;
import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import in.wynk.payment.dto.gateway.delete.DeleteCardResponse;
import in.wynk.payment.dto.gateway.delete.DeleteVpaResponse;
import in.wynk.payment.dto.request.AbstractPaymentAccountDeletionRequest;
import in.wynk.payment.gateway.IPaymentAccountDeletion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

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
public class ApsDeleteGatewayServiceImpl implements IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest> {

    private final String DELETE_CARD_ENDPOINT;
    private final String DELETE_VPA_ENDPOINT;

    private final ApsCommonGatewayService common;
    private final PaymentMethodDeletion verification = new PaymentMethodDeletion();

    public ApsDeleteGatewayServiceImpl(String deleteCardEndpoint, String deleteVpaEndpoint, ApsCommonGatewayService common) {
        this.common = common;
        this.DELETE_VPA_ENDPOINT = deleteVpaEndpoint;
        this.DELETE_CARD_ENDPOINT = deleteCardEndpoint;
    }

    @Override
    public AbstractPaymentAccountDeletionResponse delete (AbstractPaymentAccountDeletionRequest request) {
        return verification.delete(request);
    }

    private class PaymentMethodDeletion implements IPaymentAccountDeletion<AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest> {
        private final Map<DeleteType, IPaymentAccountDeletion<? extends AbstractPaymentAccountDeletionResponse, AbstractPaymentAccountDeletionRequest>> deletionDelegate = new HashMap<>();

        public PaymentMethodDeletion () {
            deletionDelegate.put(DeleteType.VPA, new VpaDeletion());
            deletionDelegate.put(DeleteType.CARD, new CardDeletion());//Card verification
        }

        @Override
        public AbstractPaymentAccountDeletionResponse delete (AbstractPaymentAccountDeletionRequest request) {
            return deletionDelegate.get(request.getDeleteType()).delete(request);
        }

        private class CardDeletion implements IPaymentAccountDeletion<DeleteCardResponse, AbstractPaymentAccountDeletionRequest> {
            @Override
            public DeleteCardResponse delete (AbstractPaymentAccountDeletionRequest request) {
                final DeleteCardRequest deleteCardRequest = DeleteCardRequest.builder().referenceNumber(request.getDeleteValue()).build();
                try {
                    Boolean response = common.exchange(request.getClient(), DELETE_CARD_ENDPOINT, HttpMethod.POST, request.getMsisdn(), deleteCardRequest, Boolean.class);
                    return DeleteCardResponse.builder().deleted(response).build();
                } catch (Exception e) {
                    if(e instanceof WynkRuntimeException) {
                        log.error(APS_SAVED_CARD_DELETION,e.getMessage());
                        return DeleteCardResponse.builder().deleted(false).build();
                    }
                    log.error(APS_SAVED_CARD_DELETION, "Card deletion failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.APS003, e);
                }
            }
        }

        private class VpaDeletion implements IPaymentAccountDeletion<DeleteVpaResponse, AbstractPaymentAccountDeletionRequest> {
            @Override
            public DeleteVpaResponse delete (AbstractPaymentAccountDeletionRequest request) {
                List<String> vpa= new ArrayList<>();
                vpa.add(request.getDeleteValue());
                final DeleteVpaRequest deleteVpaRequest = DeleteVpaRequest.builder().vpaIds(vpa).build();
                try {
                    Boolean response = common.exchange(request.getClient(), DELETE_VPA_ENDPOINT, HttpMethod.POST, request.getMsisdn(), deleteVpaRequest, Boolean.class);
                    return DeleteVpaResponse.builder().deleted(response).build();
                } catch (Exception e) {
                    log.error(APS_SAVED_VPA_DELETION, "Vpa deletion failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.APS004, e);
                }
            }
        }
    }
}
