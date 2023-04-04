package in.wynk.payment.service;

import in.wynk.payment.dto.common.AbstractSavedInstrumentInfo;

import java.util.List;

public interface IUserPaymentDetails {
    List<AbstractSavedInstrumentInfo> getSavedDetails(String userId);
}
