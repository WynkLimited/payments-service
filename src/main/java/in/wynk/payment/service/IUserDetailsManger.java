package in.wynk.payment.service;

import in.wynk.payment.dto.ICombinedUserDetails;

public interface IUserDetailsManger {

    ICombinedUserDetails save(String transactionId, ICombinedUserDetails details);

    ICombinedUserDetails get(String transactionId);

}
