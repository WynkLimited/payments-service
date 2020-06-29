package in.wynk.payment.core.constant;

public interface BeanConstant {

    String PAYU_MERCHANT_PAYMENT_SERVICE = "PayU";
    String PAYTM_MERCHANT_WALLET_SERVICE = "PayTm";
    String ITUNES_MERCHANT_PAYMENT_SERVICE = "iTunes";
    String PHONEPE_MERCHANT_PAYMENT_SERVICE = "PhonePe";
    String APB_MERCHANT_PAYMENT_SERVICE = "AirtelPaymentBank";
    String ACB_MERCHANT_PAYMENT_SERVICE = "AirtelCarrierBilling";
    String GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE = "GoogleWallet";

    String TRANSACTION_DAO = "transactionDaoBean";
    String PAYMENT_RENEWAL_DAO = "paymentRenewalDaoBean";
    String TRANSACTION_MANAGER_SERVICE = "transactionManagerBean";

    String POLLING_QUEUE_SCHEDULING_THREAD_POOL = "poolingQueueSchedulingThreadPool";

}
