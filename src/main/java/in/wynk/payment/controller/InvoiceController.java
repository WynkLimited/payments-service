package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.dto.invoice.CoreInvoiceDownloadResponse;
import in.wynk.payment.service.InvoiceManagerService;
import in.wynk.payment.utils.LoadClientUtils;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static in.wynk.common.constant.BaseConstants.TRANSACTION_ID;
import static in.wynk.payment.core.constant.PaymentConstants.TXN_ID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wynk/v1/invoice")
public class InvoiceController {

    private final InvoiceManagerService invoiceManagerService;
    @GetMapping(value = "/download/{sid}", produces = MediaType.APPLICATION_PDF_VALUE)
    @AnalyseTransaction(name = "downloadInvoice")
    @ManageSession(sessionId = "#sid")
    public ResponseEntity<byte[]> invoiceDownload(@PathVariable String sid) {
        LoadClientUtils.loadClient(false);

        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        //final String tid = "eb719093-48b6-11ee-ba71-b345982b922b";
        final String tid = sessionDTO.<String>get(TRANSACTION_ID);
        AnalyticService.update(TXN_ID, tid);

        final CoreInvoiceDownloadResponse response = invoiceManagerService.download(tid);
        final HttpHeaders headers = new HttpHeaders();
        headers.add(BaseConstants.INVOICE_ID, response.getInvoice().getId());
        headers.add("Access-Control-Expose-Headers", BaseConstants.INVOICE_ID);
        final ResponseEntity<byte[]> responseEntity = ResponseEntity.ok().headers(headers).body(response.getData());
        AnalyticService.update(responseEntity);
        return responseEntity;
    }
}
