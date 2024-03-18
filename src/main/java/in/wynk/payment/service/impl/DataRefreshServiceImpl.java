package in.wynk.payment.service.impl;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.gateway.aps.service.ApsCommonGatewayService;
import in.wynk.payment.gateway.payu.service.PayUCommonGateway;
import in.wynk.payment.service.DataRefreshService;
import in.wynk.payment.service.ITransactionManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class DataRefreshServiceImpl implements DataRefreshService {
    @Autowired
    private ApsCommonGatewayService apsCommonGatewayService;
    @Autowired
    private PayUCommonGateway payUCommonGateway;
    @Autowired
    private ITransactionManagerService transactionManagerService;

    @Override
    public void refreshMerchantTableData (String transactionId, String paymentCode) {
        Transaction transaction = transactionManagerService.get(transactionId);
        try {
            if ("aps".equalsIgnoreCase(paymentCode)) {
                apsCommonGatewayService.syncChargingTransactionFromSource(transaction, Optional.empty());
            } else if ("payu".equalsIgnoreCase(paymentCode)) {
                payUCommonGateway.syncChargingTransactionFromSource(transaction);
            }
        } catch (Exception e) {
            log.error("Exception occurred while refreshing merchnat transactio  table with PG data");
        }
    }
}
