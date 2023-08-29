package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.Invoice;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoreInvoiceDownloadResponse {
    private Invoice invoice;
    private byte[] data;
}
