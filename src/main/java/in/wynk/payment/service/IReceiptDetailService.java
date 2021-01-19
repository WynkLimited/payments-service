package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.dto.request.CallbackRequest;

import java.util.Optional;

public interface IReceiptDetailService {

    Optional<ReceiptDetails> getReceiptDetails(CallbackRequest callbackRequest);

}
