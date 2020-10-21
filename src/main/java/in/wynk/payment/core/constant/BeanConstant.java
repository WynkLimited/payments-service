package in.wynk.payment.core.constant;

public interface BeanConstant {

    String PAYU_MERCHANT_PAYMENT_SERVICE = "PayU";
    String PAYTM_MERCHANT_WALLET_SERVICE = "PayTm";
    String ITUNES_PAYMENT_SERVICE = "iTunes";
    String PHONEPE_MERCHANT_PAYMENT_SERVICE = "PhonePe";
    String APB_MERCHANT_PAYMENT_SERVICE = "AirtelPaymentBank";
    String ACB_MERCHANT_PAYMENT_SERVICE = "AirtelCarrierBilling";
    String GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE = "GoogleWallet";
    String AMAZON_IAP_PAYMENT_SERVICE = "AmazonIap";

    String PAYMENT_ERROR_DAO = "paymentErrorDao";
    String TRANSACTION_DAO = "transactionDaoBean";
    String PAYMENT_RENEWAL_DAO = "paymentRenewalDaoBean";
    String MERCHANT_TRANSACTION_DAO = "merchantTransactionDao";

    String RECURRING_PAYMENT_RENEWAL_SERVICE = "recurringPaymentRenewalManagerBean";

    String EXTERNAL_PAYMENT_CLIENT_S2S_TEMPLATE = "paymentClientHttpTemplate";
    String EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE = "paymentGatewayHttpTemplate";
    String SUBSCRIPTION_SERVICE_S2S_TEMPLATE = "subscriptionHttpTemplate";

    String PAYMENT_MONGO_TEMPLATE_REF = "paymentMongoTemplateRef";
    String PAYMENT_MONGO_DB_FACTORY_REF = "paymentMongodbFactoryRef";

}
