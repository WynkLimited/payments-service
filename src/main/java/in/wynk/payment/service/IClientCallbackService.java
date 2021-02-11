package in.wynk.payment.service;

import in.wynk.payment.dto.ClientCallbackPayloadWrapper;

public interface IClientCallbackService {

    <T> void sendCallback(ClientCallbackPayloadWrapper<T> callbackPayloadWrapper);

}
