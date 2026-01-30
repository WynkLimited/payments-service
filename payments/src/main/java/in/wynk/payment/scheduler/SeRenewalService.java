package in.wynk.payment.scheduler;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.opencsv.CSVReader;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.identity.client.utils.IdentityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.SE_BILLING;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.SE_PAYMENT_RENEWAL_ERROR;

@Slf4j
@Component
public class SeRenewalService {

    public static final String NEW_SUBS_SUCCESS_STATUS = "New-Subscription Success";

    public static final List<String> DEPROVISIONING_TRANS_TYPE_LIST = Arrays.asList("Start/Stop De-Subscription", "De-Subscription", "Churn De-Subscription", "BULK De-Subscription");

    public static final List<String> DEPROVISIONING_STATUS_LIST = Arrays.asList("Churn initiated De-Subscription Success: Postpaid", "Bulk initiated De-Subscription Success",
            "Churn initiated De-Subscription Success: Prepaid", "CP Initiated De-Subscription Success",
            "Start/Stop Initiated De-Subscription Success", "MNP initiated De-Subscription Success: Postpaid",
            "Successful Renewal Deprovisioning");

    public static final List<String> RENEW_SUBS_SUCCESS_STATUS = Arrays.asList("Charging Success", "Renewal Initiated De-Subscription Started");

    public static final String NEW_SUBS_TRANSTYPE = "New-Subscription";

    public static final String RENEW_SUBS_TRANSTYPE = "Re-Subscription";

    public static final List<String> SUSPENDED_STATUS_LIST = Arrays.asList("Customer Moved to Suspension Period");

    public static final List<String> GRACE_STATUS_LIST = Arrays.asList("Customer Moved to Grace", "Customer is in Grace Period");

    @Autowired
    private AmazonS3 amazonS3Client;
    @Autowired
    private PaymentManager paymentManager;
    @Autowired
    private WynkApplicationContext wynkApplicationContext;

    @Value("${payment.se.s3.bucket}")
    private String bucket;
    @Autowired
    private PaymentCachingService cachingService;

    public void startSeRenewal() {
        log.info("Starting SE sync task!!");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        processCsvContentForDate(cal);
        log.info("Done for today");
    }

    private void processCsvContentForDate(Calendar cal) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MMM_yyyy");
        String filePath = "SE/ht_Wynk_9038_" + dateFormat.format(cal.getTime()) + ".csv.gz";
        List<String[]> csvContent = getDownloadedContent(filePath);
        if (CollectionUtils.isEmpty(csvContent)) {
            log.info("The file: {}:{} is empty. I can take rest for today", bucket, filePath);
        }
        doSeRenewals(filePath, csvContent);
    }

    private void doSeRenewals(String filePath, List<String[]> csvContent) {
        int hits = 0;
        int count = 0;
        Map<String, String[]> map = new HashMap<>();
        for (String[] subsData : csvContent) {
            count = count + 1;
            if (count == 1) {
                continue;
            }
            String msisdn = subsData[1].trim();
            String extProductId = subsData[3].trim();
            String transactionType = subsData[5].trim();
            String transactionStatus = subsData[6].trim();
            int amount = subsData[9].trim().equalsIgnoreCase("") ? 0 : Integer.valueOf(subsData[9].trim());
            TransactionStatus seTxnStatus = getSETransactionStatus(transactionType, transactionStatus);
            if (seTxnStatus == TransactionStatus.SUCCESS && amount > 0) {
                String key = msisdn + extProductId;
                if (!map.containsKey(key)) {
                    map.put(key, subsData);
                }
            }
        }
        log.info("Total map size : {}", map.size());
        StringBuffer sb = new StringBuffer();
        for (String key : map.keySet()) {
            if (mismatchStatus(map.get(key), sb)) {
                hits++;
            }
        }
        int numOfRecords = csvContent.size() - 1;
        log.info(" Found {} mismatch of {} total records in file: {}:{}", hits, numOfRecords, bucket, filePath);
    }


    private List<String[]> getDownloadedContent(String filePath) {
        try {
            S3Object obj = amazonS3Client.getObject(new GetObjectRequest(bucket, filePath));
            InputStream is = obj.getObjectContent();

            GZIPInputStream gis = new GZIPInputStream(is);
            BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
            CSVReader reader = new CSVReader(br);
            return reader.readAll();
        } catch (Throwable e) {
            log.error(SE_PAYMENT_RENEWAL_ERROR, "Error while reading data from S3 {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @AnalyseTransaction(name = "seRenewal")
    public boolean mismatchStatus(String[] subRecord, StringBuffer sb) {
        String seRecord = ArrayUtils.toString(subRecord);
        AnalyticService.update("SE_FILE_RECORD", seRecord);
        try {
            if (subRecord.length > 0 && "TRANS_ID".equals(subRecord[0].trim())) {
                log.info("Skipping header row in se s3 reconciliation report: {}", seRecord);
                return false;
            }
            if (subRecord.length < 8) {
                log.error(SE_PAYMENT_RENEWAL_ERROR, "Invalid row in se s3 reconciliation report: {} ", seRecord);
                return false;
            }
            String extTransactionId = subRecord[0].trim();
            String msisdn = subRecord[1].trim();
            msisdn = MsisdnUtils.getTenDigitMsisdn(msisdn);
            AnalyticService.update(MSISDN, msisdn);
            String extProductId = subRecord[3].trim();
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, extTransactionId);
            String transactionDateStr = subRecord[4];
            String transactionType = subRecord[5].trim();
            String transactionStatus = subRecord[6].trim();
            AnalyticService.update(EXTERNAL_TRANSACTION_STATUS, transactionStatus);
            AnalyticService.update(EXTERNAL_PRODUCT_ID, extProductId);
            PlanDTO planDTO = cachingService.getPlanFromSku(extProductId);
            String uid = IdentityUtils.getUidFromUserName(msisdn, planDTO.getService());
            if (planDTO != null) {
                TransactionStatus seTxnStatus = getSETransactionStatus(transactionType, transactionStatus);
                AnalyticService.update(seTxnStatus);
                PaymentEvent event = getSETransactionEvent(transactionType);
                AnalyticService.update(event);
                if (seTxnStatus == TransactionStatus.SUCCESS && event != null) {
                    PaymentRenewalChargingRequest request = PaymentRenewalChargingRequest.builder()
                            .uid(uid)
                            .msisdn(msisdn)
                            .id(extTransactionId)
                            .planId(planDTO.getId())
                            .paymentGateway(PaymentCodeCachingService.getFromPaymentCode(SE_BILLING))
                            .clientAlias(wynkApplicationContext.getClientAlias())
                            .build();
                    paymentManager.doRenewal(request);
                    return true;
                } else {
                    log.info("Not verifying the transaction as the status of transaction from SE is : {} for uid : {} and event : {} and record : {}", seTxnStatus, uid, event,
                            subRecord);
                }
            }
        } catch (Throwable th) {
            log.error(SE_PAYMENT_RENEWAL_ERROR, "Error occur while reconciling the se data:{} Error: {}", subRecord, th.getMessage(), th);
        }

        return false;
    }

    private TransactionStatus getSETransactionStatus(String transactionType, String transactionStatus) {
        if (NEW_SUBS_SUCCESS_STATUS.equals(transactionStatus)) {
            return TransactionStatus.SUCCESS;
        } else if ((DEPROVISIONING_TRANS_TYPE_LIST.contains(transactionType) || DEPROVISIONING_STATUS_LIST.contains(transactionType))) {
            return TransactionStatus.FAILURE;
        } else if (RENEW_SUBS_SUCCESS_STATUS.contains(transactionStatus)) {
            return TransactionStatus.SUCCESS;
        } else {
            return TransactionStatus.FAILURE;
        }
    }

    private PaymentEvent getSETransactionEvent(String transactionType) {
        if (NEW_SUBS_TRANSTYPE.equals(transactionType)) {
            return PaymentEvent.SUBSCRIBE;
        } else if (RENEW_SUBS_TRANSTYPE.equals(transactionType)) {
            return PaymentEvent.RENEW;
        } else if ((DEPROVISIONING_TRANS_TYPE_LIST.contains(transactionType) || DEPROVISIONING_STATUS_LIST.contains(transactionType))) {
            return PaymentEvent.UNSUBSCRIBE;
        }
        return null;
    }

}