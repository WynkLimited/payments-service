package in.wynk.payment.dto.gateway;

import java.util.Optional;

public interface ICardValidationSpec {

    /**
     * Whether card is valid or not
     */
    boolean isValid();

    /**
     * signifies whether a card is domestic or international
     */
    boolean isDomestic();

    /**
     * Whether a card support otp at client payment page.
     */
    boolean isZeroRedirect();


    /**
     * A card brand (sometimes called a card network or association) is an organization that facilitates payment card transactions.
     * i.e. mastercard, visa, amex, diners_club, discover, jcb, unionpay
     */
    String getCardNetwork();


    /**
    * Bank through which the card is issued i.e. HDFC, ICICI, SBI etc
    */
    String getIssuingBank();
    /**
     * Card Type signifies card type. i.e. CREDIT, DEBIT
     */
    String getCardCategory();

}
