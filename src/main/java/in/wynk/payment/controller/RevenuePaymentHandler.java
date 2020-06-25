package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.mongodb.util.JSON;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.ApplicationConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.enums.ItunesReceiptType;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import in.wynk.payment.service.impl.ITunesMerchantPaymentService;
import in.wynk.session.aspect.advice.ManageSession;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/payment")
public class RevenuePaymentHandler {

    private final ApplicationContext context;

    public RevenuePaymentHandler(ApplicationContext context) {
        this.context = context;
    }

    @PostMapping("/charge/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCharging")
    public ResponseEntity<?> doCharging(@PathVariable String sid, @RequestBody ChargingRequest request) {
        IMerchantPaymentChargingService chargingService;
        try {
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, request.getPaymentCode().name());
            chargingService = this.context.getBean(request.getPaymentCode().getCode(), IMerchantPaymentChargingService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = chargingService.doCharging(request);
        return baseResponse.getResponse();
    }

    @GetMapping("/status/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentStatus")
    public ResponseEntity<?> status(@PathVariable String sid) {
        IMerchantPaymentStatusService statusService;
        Session<Map<String, Object>> session = SessionContextHolder.get();
        ChargingStatusRequest request = ChargingStatusRequest.builder().sessionId(session.getId().toString()).build();
        try {
            PaymentCode paymentCode = (PaymentCode) session.getBody().get(ApplicationConstant.PAYMENT_METHOD);
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentCode.name());
            statusService = this.context.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = statusService.status(request);
        return baseResponse.getResponse();
    }
//    @GetMapping("/statuss")
//    @AnalyseTransaction(name= "tempStatus")
//    public ResponseEntity<?> statuss(){
//        IMerchantPaymentStatusService statusService;
//        ChargingStatusRequest request = new ChargingStatusRequest();
//        //JSONObject jsonObject = (JSONObject) JSONValue.parse(requestPayload);
//        //request.setReceipt((String) jsonObject.get("receipt-data"));
//        request.setReceipt("{\"receipt-data\":\"MIIbtgYJKoZIhvcNAQcCoIIbpzCCG6MCAQExCzAJBgUrDgMCGgUAMIILVwYJKoZIhvcNAQcBoIILSASCC0QxggtAMAoCARQCAQEEAgwAMAsCARkCAQEEAwIBAzAMAgEOAgEBBAQCAgDLMA0CAQMCAQEEBQwDMzQ0MA0CAQoCAQEEBRYDMTIrMA0CAQsCAQEEBQIDCMPuMA0CAQ0CAQEEBQIDAf3FMA0CARMCAQEEBQwDMTg3MA4CAQECAQEEBgIEMl71MzAOAgEJAgEBBAYCBFAyNTMwDgIBEAIBAQQGAgQx1cOSMBACAQ8CAQEECAIGWhQfZqg+MBQCAQACAQEEDAwKUHJvZHVjdGlvbjAYAgEEAgECBBCo9RkZahV\\/3h\\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\\/pn+\\/8Jr9VterHNRSQhWicxDkMyOgQLQoJe2XLGhaWmHkBBoJiY5uB0Qc7AKXcVz0N92O9gt2Yge4+wHz+KO0NP6JlWB7+IDSSMCAwEAAaOCAdcwggHTMD8GCCsGAQUFBwEBBDMwMTAvBggrBgEFBQcwAYYjaHR0cDovL29jc3AuYXBwbGUuY29tL29jc3AwMy13d2RyMDQwHQYDVR0OBBYEFJGknPzEdrefoIr0TfWPNl3tKwSFMAwGA1UdEwEB\\/wQCMAAwHwYDVR0jBBgwFoAUiCcXCam2GGCL7Ou69kdZxVJUo7cwggEeBgNVHSAEggEVMIIBETCCAQ0GCiqGSIb3Y2QFBgEwgf4wgcMGCCsGAQUFBwICMIG2DIGzUmVsaWFuY2Ugb24gdGhpcyBjZXJ0aWZpY2F0ZSBieSBhbnkgcGFydHkgYXNzdW1lcyBhY2NlcHRhbmNlIG9mIHRoZSB0aGVuIGFwcGxpY2FibGUgc3RhbmRhcmQgdGVybXMgYW5kIGNvbmRpdGlvbnMgb2YgdXNlLCBjZXJ0aWZpY2F0ZSBwb2xpY3kgYW5kIGNlcnRpZmljYXRpb24gcHJhY3RpY2Ugc3RhdGVtZW50cy4wNgYIKwYBBQUHAgEWKmh0dHA6Ly93d3cuYXBwbGUuY29tL2NlcnRpZmljYXRlYXV0aG9yaXR5LzAOBgNVHQ8BAf8EBAMCB4AwEAYKKoZIhvdjZAYLAQQCBQAwDQYJKoZIhvcNAQEFBQADggEBAA2mG9MuPeNbKwduQpZs0+iMQzCCX+Bc0Y2+vQ+9GvwlktuMhcOAWd\\/j4tcuBRSsDdu2uP78NS58y60Xa45\\/H+R3ubFnlbQTXqYZhnb4WiCV52OMD3P86O3GH66Z+GVIXKDgKDrAEDctuaAEOR9zucgF\\/fLefxoqKm4rAfygIFzZ630npjP49ZjgvkTbsUxn\\/G4KT8niBqjSl\\/OnjmtRolqEdWXRFgRi48Ff9Qipz2jZkgDJwYyz+I0AZLpYYMB8r491ymm5WyrWHWhumEL1TKc3GZvMOxx6GUPzo22\\/SGAGDDaSK+zeGLUR2i0j0I78oGmcFxuegHs5R0UwYS\\/HE6gwggQiMIIDCqADAgECAggB3rzEOW2gEDANBgkqhkiG9w0BAQUFADBiMQswCQYDVQQGEwJVUzETMBEGA1UEChMKQXBwbGUgSW5jLjEmMCQGA1UECxMdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkxFjAUBgNVBAMTDUFwcGxlIFJvb3QgQ0EwHhcNMTMwMjA3MjE0ODQ3WhcNMjMwMjA3MjE0ODQ3WjCBljELMAkGA1UEBhMCVVMxEzARBgNVBAoMCkFwcGxlIEluYy4xLDAqBgNVBAsMI0FwcGxlIFdvcmxkd2lkZSBEZXZlbG9wZXIgUmVsYXRpb25zMUQwQgYDVQQDDDtBcHBsZSBXb3JsZHdpZGUgRGV2ZWxvcGVyIFJlbGF0aW9ucyBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMo4VKbLVqrIJDlI6Yzu7F+4fyaRvDRTes58Y4Bhd2RepQcjtjn+UC0VVlhwLX7EbsFKhT4v8N6EGqFXya97GP9q+hUSSRUIGayq2yoy7ZZjaFIVPYyK7L9rGJXgA6wBfZcFZ84OhZU3au0Jtq5nzVFkn8Zc0bxXbmc1gHY2pIeBbjiP2CsVTnsl2Fq\\/ToPBjdKT1RpxtWCcnTNOVfkSWAyGuBYNweV3RY1QSLorLeSUheHoxJ3GaKWwo\\/xnfnC6AllLd0KRObn1zeFM78A7SIym5SFd\\/Wpqu6cWNWDS5q3zRinJ6MOL6XnAamFnFbLw\\/eVovGJfbs+Z3e8bY\\/6SZasCAwEAAaOBpjCBozAdBgNVHQ4EFgQUiCcXCam2GGCL7Ou69kdZxVJUo7cwDwYDVR0TAQH\\/BAUwAwEB\\/zAfBgNVHSMEGDAWgBQr0GlHlHYJ\\/vRrjS5ApvdHTX8IXjAuBgNVHR8EJzAlMCOgIaAfhh1odHRwOi8vY3JsLmFwcGxlLmNvbS9yb290LmNybDAOBgNVHQ8BAf8EBAMCAYYwEAYKKoZIhvdjZAYCAQQCBQAwDQYJKoZIhvcNAQEFBQADggEBAE\\/P71m+LPWybC+P7hOHMugFNahui33JaQy52Re8dyzUZ+L9mm06WVzfgwG9sq4qYXKxr83DRTCPo4MNzh1HtPGTiqN0m6TDmHKHOz6vRQuSVLkyu5AYU2sKThC22R1QbCGAColOV4xrWzw9pv3e9w0jHQtKJoc\\/upGSTKQZEhltV\\/V6WId7aIrkhoxK6+JJFKql3VUAqa67SzCu4aCxvCmA5gl35b40ogHKf9ziCuY7uLvsumKV8wVjQYLNDzsdTJWk26v5yZXpT+RN5yaZgem8+bQp0gF6ZuEujPYhisX4eOGBrr\\/TkJ2prfOv\\/TgalmcwHFGlXOxxioK0bA8MFR8wggS7MIIDo6ADAgECAgECMA0GCSqGSIb3DQEBBQUAMGIxCzAJBgNVBAYTAlVTMRMwEQYDVQQKEwpBcHBsZSBJbmMuMSYwJAYDVQQLEx1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTEWMBQGA1UEAxMNQXBwbGUgUm9vdCBDQTAeFw0wNjA0MjUyMTQwMzZaFw0zNTAyMDkyMTQwMzZaMGIxCzAJBgNVBAYTAlVTMRMwEQYDVQQKEwpBcHBsZSBJbmMuMSYwJAYDVQQLEx1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTEWMBQGA1UEAxMNQXBwbGUgUm9vdCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOSRqQkfkdseR1DrBe1eeYQt6zaiV0xV7IsZid75S2z1B6siMALoGD74UAnTf0GomPnRymacJGsR0KO75Bsqwx+VnnoMpEeLW9QWNzPLxA9NzhRp0ckZcvVdDtV\\/X5vyJQO6VY9NXQ3xZDUjFUsVWR2zlPf2nJ7PULrBWFBnjwi0IPfLrCwgb3C2PwEwjLdDzw+dPfMrSSgayP7OtbkO2V4c1ss9tTqt9A8OAJILsSEWLnTVPA3bYharo3GSR1NVwa8vQbP4++NwzeajTEV+H0xrUJZBicR0YgsQg0GHM4qBsTBY7FoEMoxos48d3mVz\\/2deZbxJ2HafMxRloXeUyS0CAwEAAaOCAXowggF2MA4GA1UdDwEB\\/wQEAwIBBjAPBgNVHRMBAf8EBTADAQH\\/MB0GA1UdDgQWBBQr0GlHlHYJ\\/vRrjS5ApvdHTX8IXjAfBgNVHSMEGDAWgBQr0GlHlHYJ\\/vRrjS5ApvdHTX8IXjCCAREGA1UdIASCAQgwggEEMIIBAAYJKoZIhvdjZAUBMIHyMCoGCCsGAQUFBwIBFh5odHRwczovL3d3dy5hcHBsZS5jb20vYXBwbGVjYS8wgcMGCCsGAQUFBwICMIG2GoGzUmVsaWFuY2Ugb24gdGhpcyBjZXJ0aWZpY2F0ZSBieSBhbnkgcGFydHkgYXNzdW1lcyBhY2NlcHRhbmNlIG9mIHRoZSB0aGVuIGFwcGxpY2FibGUgc3RhbmRhcmQgdGVybXMgYW5kIGNvbmRpdGlvbnMgb2YgdXNlLCBjZXJ0aWZpY2F0ZSBwb2xpY3kgYW5kIGNlcnRpZmljYXRpb24gcHJhY3RpY2Ugc3RhdGVtZW50cy4wDQYJKoZIhvcNAQEFBQADggEBAFw2mUwteLftjJvc83eb8nbSdzBPwR+Fg4UbmT1HN\\/Kpm0COLNSxkBLYvvRzm+7SZA\\/LeU802KI++Xj\\/a8gH7H05g4tTINM4xLG\\/mk8Ka\\/8r\\/FmnBQl8F0BWER5007eLIztHo9VvJOLr0bdw3w9F4SfK8W147ee1Fxeo3H4iNcol1dkP1mvUoiQjEfehrI9zgWDGG1sJL5Ky+ERI8GA4nhX1PSZnIIozavcNgs\\/e66Mv+VNqW2TAYzN39zoHLFbr2g8hDtq6cxlPtdk2f8GHVdmnmbkyQvvY1XGefqFStxu9k0IkEirHDx22TZxeY8hLgBdQqorV2uT80AkHN7B1dSExggHLMIIBxwIBATCBozCBljELMAkGA1UEBhMCVVMxEzARBgNVBAoMCkFwcGxlIEluYy4xLDAqBgNVBAsMI0FwcGxlIFdvcmxkd2lkZSBEZXZlbG9wZXIgUmVsYXRpb25zMUQwQgYDVQQDDDtBcHBsZSBXb3JsZHdpZGUgRGV2ZWxvcGVyIFJlbGF0aW9ucyBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eQIIDutXh+eeCY0wCQYFKw4DAhoFADANBgkqhkiG9w0BAQEFAASCAQA\\/lw9ekFGJdyzc6s8iBD8CNvJRNrt9gbkw2eUKSBIjPtUzrnnlHZBMu\\/t8iiSzP14FoNRd2Yed+noaRlEiC6iyc1o5+PAP1Is9YoQ2taaXvuChT7e0FzACBtQCFxxsTZjUNfd0CRiHRe\\/EhX+lfQrzLfMQKZDXRfbBy84L+IKTjd67ogxVB8JS99SuvtXUgpOM47\\/WujrLhYtrqoE\\/yZm+t74jtg5hbPgXwz8RHqB+8rUG+PsJfwo6JaXx8Oh\\/J1rTVopgdkF0kiu1bXlzUVQJoNAwpSLudDPhbkbVU3psBg6lL\\/3aUqeCuqp8MEF2z2zHjy70yueVIf20xyhfoTrQ\"}"
//        );
//        request.setTransactionId("sdcvfv");
//        request.setUid("sdvv");
//        request.setUid("dsvwv0");
//        //Session<Map<String, Object>> session = SessionContextHolder.get();
//        //ChargingStatusRequest request = ChargingStatusRequest.builder().sessionId(session.getId().toString()).build();
//        try {
//            //PaymentOption paymentOption = (PaymentOption) session.getBody().get(ApplicationConstant.PAYMENT_METHOD);
//            //AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, paymentOption.name());
//            PaymentOption paymentOption = PaymentOption.ITUNES;
//            statusService = this.context.getBean(paymentOption.getType(), IMerchantPaymentStatusService.class);
//        } catch (BeansException e) {
//            throw new WynkRuntimeException(PaymentErrorType.PAY001);
//        }
//        BaseResponse<?> baseResponse = statusService.status(request);
//        return baseResponse.getResponse();
//    }

    @PostMapping("/callback/{sid}")
    @ManageSession(sessionId = "#sid")
    @AnalyseTransaction(name = "paymentCallback")
    public ResponseEntity<?> handleCallback(@PathVariable String sid, @RequestBody Map<String, Object> payload) {
        IMerchantPaymentCallbackService callbackService;
        Session<Map<String, Object>> session = SessionContextHolder.get();
        CallbackRequest<Map<String, Object>> request = CallbackRequest.<Map<String, Object>>builder().body(payload).build();
        try {
            PaymentCode option = ((PaymentCode) session.getBody().get(ApplicationConstant.PAYMENT_METHOD));
            AnalyticService.update(ApplicationConstant.PAYMENT_METHOD, option.name());
            AnalyticService.update(ApplicationConstant.REQUEST_PAYLOAD, payload.toString());
            callbackService = this.context.getBean(option.getCode(), IMerchantPaymentCallbackService.class);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY001);
        }
        BaseResponse<?> baseResponse = callbackService.handleCallback(request);
        return baseResponse.getResponse();
    }

}
