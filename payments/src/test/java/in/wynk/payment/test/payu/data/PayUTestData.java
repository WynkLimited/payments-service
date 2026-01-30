package in.wynk.payment.test.payu.data;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentDetails;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.test.payu.constant.PayUDataConstant;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import in.wynk.subscription.common.dto.PriceDTO;
import in.wynk.subscription.common.enums.PlanType;
import in.wynk.subscription.common.response.AllPlansResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYU;
import static in.wynk.payment.dto.payu.PayUConstants.*;

public class PayUTestData {

    public static Transaction initOneTimePaymentTransaction() {
        return Transaction.builder()
                .paymentChannel("PAYU")
                .uid(PayUDataConstant.UID)
                .msisdn(PayUDataConstant.MSISDN)
                .consent(Calendar.getInstance())
                .initTime(Calendar.getInstance())
                .type(PaymentEvent.PURCHASE.name())
                .planId(PayUDataConstant.ONE_TIME_PLAN_ID)
                .status(TransactionStatus.INPROGRESS.name())
                .amount(PayUDataConstant.SELECTED_PLAN_AMOUNT)
                .id(PayUDataConstant.ONE_TIME_TRANSACTION_ID.toString())
                .build();
    }

    public static Transaction initRecurringPaymentTransaction() {
        return Transaction.builder()
                .paymentChannel("PAYU")
                .uid(PayUDataConstant.UID)
                .msisdn(PayUDataConstant.MSISDN)
                .consent(Calendar.getInstance())
                .initTime(Calendar.getInstance())
                .type(PaymentEvent.PURCHASE.name())
                .planId(PayUDataConstant.RECURRING_PLAN_ID)
                .status(TransactionStatus.INPROGRESS.name())
                .amount(PayUDataConstant.SELECTED_PLAN_AMOUNT)
                .id(PayUDataConstant.RECURRING_TRANSACTION_ID.toString())
                .build();
    }

    public static Transaction initRecurringSubscribeTransaction() {
        return Transaction.builder()
                .paymentChannel("PAYU")
                .uid(PayUDataConstant.UID)
                .consent(Calendar.getInstance())
                .msisdn(PayUDataConstant.MSISDN)
                .initTime(Calendar.getInstance())
                .type(PaymentEvent.SUBSCRIBE.name())
                .planId(PayUDataConstant.RECURRING_PLAN_ID)
                .status(TransactionStatus.INPROGRESS.name())
                .amount(PayUDataConstant.SELECTED_PLAN_AMOUNT)
                .id(PayUDataConstant.RECURRING_TRANSACTION_ID.toString())
                .build();
    }

    public static Session<String, SessionDTO> initSession() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(UID, PayUDataConstant.UID);
        payload.put(MSISDN, PayUDataConstant.MSISDN);
        payload.put(APP_ID, PayUDataConstant.APP_ID);
        payload.put(DEVICE_ID, PayUDataConstant.DEVICE_ID);
        payload.put(APP_VERSION, PayUDataConstant.APP_VERSION);
        payload.put(BUILD_NO, PayUDataConstant.BUILD_NO);
        payload.put(OS, PayUDataConstant.OS);
        payload.put(PACK_GROUP, "");
        return Session.<String, SessionDTO>builder()
                .id(UUID.randomUUID().toString())
                .body(SessionDTO.builder().sessionPayload(payload).build())
                .build();
    }

    public static PlanDTO getPlanOfType(int planID, PlanType planType) {
        return PlanDTO.builder()
                .id(planID)
                .title(PayUDataConstant.PLAN_TITTLE)
                .planType(planType)
                .price(PriceDTO.builder().amount(PayUDataConstant.SELECTED_PLAN_AMOUNT).currency(PayUDataConstant.CURRENCY_TYPE).build())
                .period(PlanPeriodDTO.builder().validity(100).timeUnit(TimeUnit.DAYS).retryInterval(1).build())
                .build();
    }

    public static AbstractChargingRequest<?> buildOneTimeChargingRequest() {
        return DefaultChargingRequest.builder().paymentCode(PAYU).purchaseDetails(WebPurchaseDetails.builder().paymentDetails(PaymentDetails.builder().build()).productDetails(PlanDetails.builder().planId(PayUDataConstant.ONE_TIME_PLAN_ID).build()).build()).build();
    }

    public static AbstractChargingRequest<?> buildRecurringChargingRequest() {
        return DefaultChargingRequest.builder().paymentCode(PAYU).purchaseDetails(WebPurchaseDetails.builder().paymentDetails(PaymentDetails.builder().build()).productDetails(PlanDetails.builder().planId(PayUDataConstant.RECURRING_PLAN_ID).build()).build()).build();
    }

    public static CallbackRequest buildOneTimeCallbackRequest() {
        Map<String, Object> map = new HashMap<>();
        map.put("mihpayid", "10616026107");
        map.put("mode", "CC");
        map.put("status", "success");
        map.put("unmappedstatus", "captured");
        map.put("key", "aU2Uoi");
        map.put("txnid", "5a9119b0bae411eab235c98f59a5b6411111");
        map.put("amount", 1.00);
        map.put("cardCategory", "domestic");
        map.put("discount", 0.00);
        map.put("net_amount_debit", 1);
        map.put("addedon", "2020-06-30+20,47,36");
        map.put("productinfo", "Monthly");
        map.put("firstname", "B8NClsZs5cnDYbsHS0");
        map.put("lastname", "");
        map.put("address1", "");
        map.put("address2", "");
        map.put("city", "");
        map.put("state", "");
        map.put("country", "");
        map.put("zipcode", "");
        map.put("email", "B8NClsZs5cnDYbsHS0@wynk.in");
        map.put("phone", "8887528761");
        map.put("udf1", "");
        map.put("udf2", "");
        map.put("udf3", "");
        map.put("udf4", "");
        map.put("udf5", "");
        map.put("udf6", "");
        map.put("udf7", "");
        map.put("udf8", "");
        map.put("udf9", "");
        map.put("udf10", "");
        map.put("hash", PayUDataConstant.SUCCESS_ONE_TIME_CALLBACK_PAYU_HASH);
        map.put("field1", "5935302770396731405036");
        map.put("field2", "060812");
        map.put("field3", 1.00);
        map.put("field4", "10616026107");
        map.put("field5", 100);
        map.put("field6", "05");
        map.put("field7", "AUTHPOSITIVE");
        map.put("field8", "");
        map.put("field9", "Transaction+is+Successful");
        map.put("payment_source", "payu");
        map.put("PG_TYPE", "AxisCYBER");
        map.put("bank_ref_num", "5935302770396731405036");
        map.put("bankcode", "CC");
        map.put("error", "E000");
        map.put("error_Message", "No+Error");
        map.put("cardToken", "2aec9d61ceb65918965823");
        map.put("name_on_card", "SICard");
        map.put("cardnum", "489377XXXXXX2986");
        map.put("cardhash", "This+field+is+no+longer+supported+in+postback+params");
        return CallbackRequestWrapper.builder().payload(map).build();
    }

    public static CallbackRequest buildRecurringCallbackRequest() {
        Map<String, Object> map = new HashMap<>();
        map.put("mihpayid", "10616026107");
        map.put("mode", "CC");
        map.put("status", "success");
        map.put("unmappedstatus", "captured");
        map.put("key", "aU2Uoi");
        map.put("txnid", "5a9119b0bae411eab235c98f59a5b6411111");
        map.put("amount", 1.00);
        map.put("cardCategory", "domestic");
        map.put("discount", 0.00);
        map.put("net_amount_debit", 1);
        map.put("addedon", "2020-06-30+20,47,36");
        map.put("productinfo", "Monthly");
        map.put("firstname", "B8NClsZs5cnDYbsHS0");
        map.put("lastname", "");
        map.put("address1", "");
        map.put("address2", "");
        map.put("city", "");
        map.put("state", "");
        map.put("country", "");
        map.put("zipcode", "");
        map.put("email", "B8NClsZs5cnDYbsHS0@wynk.in");
        map.put("phone", "8887528761");
        map.put("udf1", "si");
        map.put("udf2", "");
        map.put("udf3", "");
        map.put("udf4", "");
        map.put("udf5", "");
        map.put("udf6", "");
        map.put("udf7", "");
        map.put("udf8", "");
        map.put("udf9", "");
        map.put("udf10", "");
        map.put("hash", PayUDataConstant.SUCCESS_RECURRING_CALLBACK_PAYU_HASH);
        map.put("field1", "5935302770396731405036");
        map.put("field2", "060812");
        map.put("field3", 1.00);
        map.put("field4", "10616026107");
        map.put("field5", 100);
        map.put("field6", "05");
        map.put("field7", "AUTHPOSITIVE");
        map.put("field8", "");
        map.put("field9", "Transaction+is+Successful");
        map.put("payment_source", "payu");
        map.put("PG_TYPE", "AxisCYBER");
        map.put("bank_ref_num", "5935302770396731405036");
        map.put("bankcode", "CC");
        map.put("error", "E000");
        map.put("error_Message", "No+Error");
        map.put("cardToken", "2aec9d61ceb65918965823");
        map.put("name_on_card", "SICard");
        map.put("cardnum", "489377XXXXXX2986");
        map.put("cardhash", "This+field+is+no+longer+supported+in+postback+params");
        return CallbackRequestWrapper.builder().payload(map).build();
    }

    public static MultiValueMap<String, String> buildOneTimePayUTransactionStatusRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.VERIFY_PAYMENT.getCode());
        requestMap.add(PAYU_HASH, "298b545adbddc18b5375d19a89e6445f50f1cce38815b71b6969f88fa8114387c577222d04901b1381f43d8a6ac0e6f1523534398648a698065eabae0c66fd68");
        requestMap.add(PAYU_VARIABLE1, PayUDataConstant.ONE_TIME_TRANSACTION_ID.toString());
        return requestMap;
    }

    public static MultiValueMap<String, String> buildRecurringPayUTransactionStatusRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.VERIFY_PAYMENT.getCode());
        requestMap.add(PAYU_HASH, "476a552efcb08b91947af9d5e40eaec7c910091df89a37661a92f83f746ec5d685d6adca251add25d25ef9861ccf165434382056a29978814499194fba3200fa");
        requestMap.add(PAYU_VARIABLE1, PayUDataConstant.RECURRING_TRANSACTION_ID.toString());
        return requestMap;
    }

    public static MultiValueMap<String, String> buildValidVPAVerificationRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.VERIFY_VPA.getCode());
        requestMap.add(PAYU_HASH, "a5c697536498b869b307a00d29ed7a657ac211cc6d1f2d201d8a84b13ddb0c0b272d614342e01e426bd97795ba5fd6184e8a62f54adb683812803e0babbe1139");
        requestMap.add(PAYU_VARIABLE1, "valid_random_vpa");
        return requestMap;
    }

    public static MultiValueMap<String, String> buildValidBINVerificationRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.CARD_BIN_INFO.getCode());
        requestMap.add(PAYU_HASH, "d3984b2ecb1681ea394aebef57bbeba0f620a88f54513fce566b58234c494ac5a763b90e4583b6f91333750c918a45080a548dc7d6ea15338a63d2ae4be90843");
        requestMap.add(PAYU_VARIABLE1, "valid_random_bin");
        return requestMap;
    }

    public static MultiValueMap<String, String> buildInValidVPAVerificationRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.VERIFY_VPA.getCode());
        requestMap.add(PAYU_HASH, "286543cb05761157af6659e6a12ecf6e5d5e70507c250393f467e9ab17457a67e82c7bbffd56403f025cbda18a83f9a2ff3276cbed14ebbb50ae44229d69b617");
        requestMap.add(PAYU_VARIABLE1, "invalid_random_vpa");
        return requestMap;
    }

    public static MultiValueMap<String, String> buildInValidBINVerificationRequest(String payUMerchantKey) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.CARD_BIN_INFO.getCode());
        requestMap.add(PAYU_HASH, "3aa08aac17d18e686b1e22e9249ea87b5880861089657d33bcfdb287dc113eeb1410808944d2e9a0ea89c7e1e7a7657eb2197f7a59aad21a10a6ddc0d44cff7f");
        requestMap.add(PAYU_VARIABLE1, "invalid_random_bin");
        return requestMap;
    }

    public static String buildSuccessOneTimePayUTransactionStatusResponse() {
        return PayUDataConstant.SUCCESS_ONE_TIME_PAYU_TRANSACTION_STATUS;
    }

    public static String buildSuccessRecurringPayUTransactionStatusResponse() {
        return PayUDataConstant.SUCCESS_RECURRING_PAYU_TRANSACTION_STATUS;
    }

    public static String buildValidVPAPayUTransactionStatusResponse() {
        return PayUDataConstant.VALID_VPA_VERIFICATION_RESPONSE;
    }

    public static String buildValidBINPayUTransactionStatusResponse() {
        return PayUDataConstant.VALID_BIN_VERIFICATION_RESPONSE;
    }

    public static String buildInvalidVPAPayUTransactionStatusResponse() {
        return PayUDataConstant.INVALID_VPA_VERIFICATION_RESPONSE;
    }

    public static String buildInvalidBINPayUTransactionStatusResponse() {
        return PayUDataConstant.INVALID_BIN_VERIFICATION_RESPONSE;
    }

    public static String buildFailurePayUTransactionStatusResponse() {
        return PayUDataConstant.FAILURE_PAYU_TRANSACTION_STATUS;
    }

    public static String buildPendingPayUTransactionStatusResponse() {
        return PayUDataConstant.PENDING_PAYU_TRANSACTION_STATUS;
    }

    public static String buildUnknownPayUTransactionStatusResponse() {
        return PayUDataConstant.UNKNOWN_PAYU_TRANSACTION_STATUS;
    }

    public static AbstractTransactionStatusRequest buildOneTimePaymentStatusRequest(PaymentGateway pg) {
        return ChargingTransactionReconciliationStatusRequest.builder()
                .transactionId(PayUDataConstant.ONE_TIME_TRANSACTION_ID.toString())
                .build();
    }

    public static AbstractTransactionStatusRequest buildRecurringPaymentStatusRequest(PaymentGateway pg) {
        return ChargingTransactionReconciliationStatusRequest.builder()
                .transactionId(PayUDataConstant.RECURRING_TRANSACTION_ID.toString())
                .build();
    }


    public static PaymentRenewalChargingRequest buildPaymentRenewalChargingRequest() {
        return PaymentRenewalChargingRequest.builder()
                .paymentCode("payu").clientAlias("music").attemptSequence(1)
                .paymentGateway(PaymentGateway.builder().internalRecurring(true).code("payu").preDebit(true).id("PAYU").build())
                .uid(PayUDataConstant.UID)
                .msisdn(PayUDataConstant.MSISDN)
                .planId(PayUDataConstant.RECURRING_PLAN_ID)
                .id(PayUDataConstant.RECURRING_TRANSACTION_ID.toString())
                .build();
    }

    public static Stream<PaymentRenewal> buildPaymentRenewalTestData() {
        PaymentRenewal paymentRenewal1 = PaymentRenewal.builder().transactionEvent(PaymentEvent.POINT_PURCHASE.getValue()).transactionId("id1").build();
        PaymentRenewal paymentRenewal2 = PaymentRenewal.builder().transactionEvent(PaymentEvent.PURCHASE.getValue()).transactionId("id2").build();
        PaymentRenewal paymentRenewal3 = PaymentRenewal.builder().transactionEvent(PaymentEvent.RENEW.getValue()).transactionId("id3").build();
        PaymentRenewal paymentRenewal4 = PaymentRenewal.builder().transactionEvent(PaymentEvent.SUBSCRIBE.getValue()).transactionId("id4").build();
        PaymentRenewal paymentRenewal5 = PaymentRenewal.builder().transactionEvent(PaymentEvent.UNSUBSCRIBE.getValue()).transactionId("id5").build();
        return Stream.of(paymentRenewal1, paymentRenewal2, paymentRenewal3, paymentRenewal4, paymentRenewal5);
    }

    public static ResponseEntity<AllPlansResponse> buildAllPlanResponse() {
        return new ResponseEntity<>(AllPlansResponse.builder().plans(Collections.EMPTY_LIST).build(), HttpStatus.OK);
    }

}