package in.wynk.payment.dto.gateway;

import java.util.Optional;

public interface IUpiIntentSpec {

    /**
     * pa: This is mandatory parameter
     * ‘Payee VPA’ to be given by Paytm to each merchant.
     * */
    String getPayeeVpa();
    /**
     * pn: This is mandatory parameter ‘Payee display name’.
     * This name will be shown in UPI apps while payment.
     * */
    String getPayeeDisplayName();
    /**
     *  tr: This is also mandatory parameter ‘Merchant Order ID’.
     *  This order id provided by your app for that order.
     *  Using this order id your backend server will track and confirm the payment for that particular order.
     * */
    String getMerchantOrderID();

    /**
     * mc: This is mandatory parameter ‘merchant category code’ to be given by Paytm to each merchant.
     * */
    String getMerchantCategoryCode();

    /**
     * am: This is also mandatory parameter ‘amount’ to be paid. This is total amount to paid by customer to merchant.
     * */
    String getAmountToBePaid();

    /**
     * tn: This is optional parameter ‘transaction note’. This is any sentence that you want to add with payment.
     * */
    Optional<String> getTransactionNote();

    /**
     * This is optional parameter `Currency Code`. This is currency code in which payment needs ot be collected.
     * */
    Optional<String> getCurrencyCode();
}
