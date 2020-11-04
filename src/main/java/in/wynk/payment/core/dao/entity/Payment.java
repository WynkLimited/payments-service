package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.enums.PaymentGroup;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "group")
@JsonSubTypes({@JsonSubTypes.Type(value = Card.class, name = "CARD"),
        @JsonSubTypes.Type(value = Wallet.class, name = "WALLET")})
public interface Payment {
    PaymentGroup getGroup();

    PaymentCode getPaymentCode();
}

