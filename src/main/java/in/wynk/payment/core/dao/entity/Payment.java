package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "group")
@JsonSubTypes({@JsonSubTypes.Type(value = Card.class, name = "CARD"),
        @JsonSubTypes.Type(value = Wallet.class, name = "WALLET")})
@NoArgsConstructor
@SuperBuilder
public abstract class Payment {
    abstract PaymentGroup getGroup();
    abstract PaymentCode getPaymentCode();
}

