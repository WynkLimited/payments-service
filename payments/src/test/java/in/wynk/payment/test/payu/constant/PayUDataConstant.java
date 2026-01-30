package in.wynk.payment.test.payu.constant;

import java.util.UUID;

public interface PayUDataConstant {

    UUID ONE_TIME_TRANSACTION_ID = UUID.fromString("e99fff07-849e-4f99-ab69-5ddc11211378");
    UUID RECURRING_TRANSACTION_ID = UUID.fromString("c4c0bbd3-ab9c-4753-a95a-0e4e81740ef6");
    String APP_ID = "randomappid";
    String APP_VERSION = "1.16.10";
    String DEVICE_ID = "2ee333c0eec277f5";
    String UID = "KUD7ybaFUcPnSfMj10";
    String MSISDN = "+919457778448";
    String OS = "ANDROID";
    String PLAN_TITTLE = "Dummy plans";
    String CURRENCY_TYPE = "INR";
    String SUCCESS_ONE_TIME_CALLBACK_PAYU_HASH = "7940481292b72cf7358144f16092ce4ea3b46f00c3b81bb6d62b648c0c2c0dfad7503c7a588f7217f88da3b8a2a4e842449afaa292f9cd29a9ed4992e0334806";
    String SUCCESS_RECURRING_CALLBACK_PAYU_HASH = "2e361dc7085b7d956a383cbcfcb4fe3d69e16f7d02576eb32718fce35bb4dbf502d1d5615d054bac11c5e924f75f55d01c11a7206129bdf945ddd54786450b1d";
    int BUILD_NO = 12605;
    int ONE_TIME_PLAN_ID = 1000182;
    int RECURRING_PLAN_ID = 1000181;
    double SELECTED_PLAN_AMOUNT = 1.00;


    String SUCCESS_ONE_TIME_PAYU_TRANSACTION_STATUS = "{\"status\":1,\"msg\":\"1 out of 1 transaction fetched\",\"transaction_details\": { \"e99fff07-849e-4f99-ab69-5ddc11211378\" : {\"status\":\"success\",\"mihpayid\":\"asnaksknqwnkekwe12123\",\"addedon\":\"2016-12-18@07:53:34.740+0000\",\"error_code\":\"NO ERROR\",\"error_Message\":\"NO ERROR\",\"udf1\":\"\",\"card_no\":\"213232xxxxx23232\",\"payuid\":\"adsdsdf\",\"transactionid\":\"e99fff07-849e-4f99-ab69-5ddc11211378\",\"field9\":\"NO ERROR\"}}}";
    String SUCCESS_RECURRING_PAYU_TRANSACTION_STATUS = "{\"status\":1,\"msg\":\"1 out of 1 transaction fetched\",\"details\": { \"c4c0bbd3-ab9c-4753-a95a-0e4e81740ef6\" : {\"status\":\"success\",\"mihpayid\":\"asnaksknqwnkekwe12123\",\"addedon\":\"2016-12-18@07:53:34.740+0000\",\"error_code\":\"NO ERROR\",\"error_Message\":\"NO ERROR\",\"udf1\":\"si\",\"card_no\":\"213232xxxxx23232\",\"payuid\":\"adsdsdf\",\"transactionid\":\"c4c0bbd3-ab9c-4753-a95a-0e4e81740ef6\",\"field9\":\"NO ERROR\"}}}";
    String VALID_VPA_VERIFICATION_RESPONSE = "{\"status\": \"success\", \"vpa\": \"random@iciciok\", \"isVPAValid\": 1, \"payerAccountName\": \"random\"}";
    String VALID_BIN_VERIFICATION_RESPONSE = "{\"issuingBank\": \"icici\", \"isDomestic\": \"Y\", \"cardType\": \"creditcard\", \"cardCategory\": \"CC\"}";
    String INVALID_VPA_VERIFICATION_RESPONSE = "{\"status\": \"success\", \"vpa\": \"random@iciciok\", \"isVPAValid\": 0, \"payerAccountName\": \"random\"}";
    String INVALID_BIN_VERIFICATION_RESPONSE = "{\"issuingBank\": \"icici\", \"isDomestic\": \"N\", \"cardType\": \"creditcard\", \"cardCategory\": \"CC\"}";
    String FAILURE_PAYU_TRANSACTION_STATUS = "{\"status\":1,\"msg\":\"1 out of 1 transaction fetched\",\"transaction_details\": { \"e99fff07-849e-4f99-ab69-5ddc11211378\" : {\"status\":\"success\",\"mihpayid\":\"asnaksknqwnkekwe12123\",\"addedon\":\"2016-12-18@07:53:34.740+0000\",\"error_code\":\"NO ERROR\",\"error_Message\":\"NO ERROR\",\"udf1\":\"\",\"card_no\":\"213232xxxxx23232\",\"payuid\":\"adsdsdf\",\"transactionid\":\"e99fff07-849e-4f99-ab69-5ddc11211378\",\"field9\":\"NO ERROR\"}}}";
    String PENDING_PAYU_TRANSACTION_STATUS = "{\"status\":1,\"msg\":\"1 out of 1 transaction fetched\",\"transaction_details\": { \"e99fff07-849e-4f99-ab69-5ddc11211378\" : {\"status\":\"success\",\"mihpayid\":\"asnaksknqwnkekwe12123\",\"addedon\":\"2016-12-18@07:53:34.740+0000\",\"error_code\":\"NO ERROR\",\"error_Message\":\"NO ERROR\",\"udf1\":\"\",\"card_no\":\"213232xxxxx23232\",\"payuid\":\"adsdsdf\",\"transactionid\":\"e99fff07-849e-4f99-ab69-5ddc11211378\",\"field9\":\"NO ERROR\"}}}";
    String UNKNOWN_PAYU_TRANSACTION_STATUS = "{\"status\":1,\"msg\":\"1 out of 1 transaction fetched\",\"transaction_details\": { \"e99fff07-849e-4f99-ab69-5ddc11211378\" : {\"status\":\"success\",\"mihpayid\":\"asnaksknqwnkekwe12123\",\"addedon\":\"2016-12-18@07:53:34.740+0000\",\"error_code\":\"NO ERROR\",\"error_Message\":\"NO ERROR\",\"udf1\":\"\",\"card_no\":\"213232xxxxx23232\",\"payuid\":\"adsdsdf\",\"transactionid\":\"e99fff07-849e-4f99-ab69-5ddc11211378\",\"field9\":\"NO ERROR\"}}}";


}
