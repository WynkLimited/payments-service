package in.wynk.payment.test.payu;

import in.wynk.payment.test.payu.testcases.callback.PayUPaymentCallbackTest;
import in.wynk.payment.test.payu.testcases.charging.PayUPaymentChargingTest;
import in.wynk.payment.test.payu.testcases.renew.PayUPaymentRenewTest;
import in.wynk.payment.test.payu.testcases.status.PayUPaymentStatusTest;
import in.wynk.payment.test.payu.testcases.verify.PayUPaymentVerifyTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PayUPaymentChargingTest.class,
        PayUPaymentCallbackTest.class,
        PayUPaymentStatusTest.class,
        PayUPaymentVerifyTest.class,
        PayUPaymentRenewTest.class
})
@TestMethodOrder(OrderAnnotation.class)
public class PayUPaymentTestSuite {
}
