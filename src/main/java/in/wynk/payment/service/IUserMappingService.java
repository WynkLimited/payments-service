package in.wynk.payment.service;

import in.wynk.payment.dto.request.UserMappingRequest;

public interface IUserMappingService {

    void addUserMapping(UserMappingRequest request);
}
