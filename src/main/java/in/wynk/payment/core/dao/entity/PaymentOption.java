package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.commons.enums.PaymentGroup;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "group")
@JsonSubTypes({@JsonSubTypes.Type(value = Cards.class, name = "CARD"),
        @JsonSubTypes.Type(value = Wallets.class, name = "WALLET")})
public interface PaymentOption {
    PaymentGroup getGroup();
}

