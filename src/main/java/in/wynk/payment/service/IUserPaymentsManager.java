package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.UserPreferredPayment;

import java.util.List;

public interface IUserPaymentsManager {

    List<UserPreferredPayment> get(String uid);

    void save(UserPreferredPayment userPreferredPayment);

    void delete(UserPreferredPayment userPreferredPayment);

}