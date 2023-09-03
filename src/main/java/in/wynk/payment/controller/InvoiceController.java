package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.dto.invoice.CoreInvoiceDownloadResponse;
import in.wynk.payment.service.InvoiceManagerService;
import in.wynk.payment.utils.LoadClientUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/invoice")
public class InvoiceController {

    private final InvoiceManagerService invoiceManagerService;
    @GetMapping(value = "/download/{tid}", produces = MediaType.APPLICATION_PDF_VALUE)
    @AnalyseTransaction(name = "downloadInvoice")
    public ResponseEntity<byte[]> invoiceDownload(@PathVariable String tid) {
        LoadClientUtils.loadClient(true);
        AnalyticService.update(TXN_ID, tid);
        /*final WynkResponseEntity<byte[]> responseEntity = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IPaymentPresentationV2<byte[], byte[]>>() {
                }).transform(() -> invoiceManagerService.download(tid));
        responseEntity.getHeaders().add("invoiceID","");
        AnalyticService.update(responseEntity);*/
        final CoreInvoiceDownloadResponse response = invoiceManagerService.download(tid);
        final HttpHeaders headers = new HttpHeaders();
        headers.add(BaseConstants.INVOICE_ID, response.getInvoice().getId());
        final ResponseEntity<byte[]> responseEntity = ResponseEntity.ok().headers(headers).body(response.getData());
        AnalyticService.update(responseEntity);
        return responseEntity;
    }
}
