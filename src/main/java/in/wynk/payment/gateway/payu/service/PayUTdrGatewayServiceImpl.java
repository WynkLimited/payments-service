package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.payu.PayUTdrResponse;
import in.wynk.payment.service.IMerchantTDRService;
import in.wynk.payment.service.IMerchantTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_TDR_ERROR;
import static in.wynk.payment.dto.payu.PayUCommand.PAYU_GETTDR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class PayUTdrGatewayServiceImpl implements IMerchantTDRService {
    private final String TDR_ENDPOINT;
    private final PayUCommonGateway common;
    private final IMerchantTransactionService merchantTransactionService;

    public PayUTdrGatewayServiceImpl (String payuInfoApi, PayUCommonGateway common, IMerchantTransactionService merchantTransactionService) {
        this.common = common;
        this.merchantTransactionService = merchantTransactionService;
        this.TDR_ENDPOINT = payuInfoApi;
    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        try {
            final Transaction transaction = TransactionContext.get();
            final MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transactionId);
            final String midPayId = merchantTransaction.getExternalTransactionId();
            final MultiValueMap<String, String> requestMap = common.buildPayUInfoRequest(transaction.getClientAlias(), PAYU_GETTDR.getCode(), midPayId);
            final PayUTdrResponse response = common.exchange(TDR_ENDPOINT, requestMap, new TypeReference<PayUTdrResponse>() {
            });
            return BaseTDRResponse.from(response.getMessage().getTdr());
        } catch (Exception e) {
            log.error(PAYU_TDR_ERROR, e.getMessage());
        }
        return BaseTDRResponse.from(-2);
    }
}
