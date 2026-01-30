package in.wynk.payment.scheduler;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.dao.entity.PaymentDump;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.logging.BaseLoggingMarkers.MYSQL_ERROR;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.payment.core.constant.BeanConstant.TRANSACTION_MANAGER;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_DUMP;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_TRANSACTION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_SERVICE_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.SDK_CLIENT_ERROR;

@Slf4j
@Service
@Transactional(transactionManager = TRANSACTION_MANAGER)
public class PaymentDumpService {

    @Autowired
    private Gson gson;

    @Value("${payment.se.s3.bucket}")
    private String bucket;

    @Autowired
    private AmazonS3 amazonS3Client;

    private void putTransactionDataOnS3(Calendar cal, int days) {
        try {
            List<Transaction> transactionList = getPaymentDbDump(cal, days).getTransactions().collect(Collectors.toList());
            AnalyticService.update("TotalRecordsInDump", transactionList.size());
            cal.add(Calendar.DATE, days);
            try {
                putTransactionsOnS3Bucket(transactionList, cal);
            } catch (AmazonServiceException ex) {
                AnalyticService.update(AMAZON_SERVICE_ERROR.getName(), ex.getErrorMessage());
                log.error(AMAZON_SERVICE_ERROR, "AmazonServiceException " + ex.getErrorMessage());
            } catch (SdkClientException e) {
                AnalyticService.update(SDK_CLIENT_ERROR.getName(), e.getMessage());
                log.error(SDK_CLIENT_ERROR, "SdkClientException " + e.getMessage());
            }
        } catch (Exception ex) {
            AnalyticService.update(MYSQL_ERROR.getName(), ex.getMessage());
            log.error(MYSQL_ERROR, "Unable to load mySql db " + ex.getMessage());
        }
    }

    private PaymentDump getPaymentDbDump(Calendar cal, int days) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String endDateFormat = dateFormat.format(cal.getTime());
        AnalyticService.update("EndDateOfDump", endDateFormat);
        Date endDate = dateFormat.parse(endDateFormat);
        log.info("end Date {}", endDate);
        cal.add(Calendar.DATE, -days);
        String fromDateFormat = dateFormat.format(cal.getTime());
        AnalyticService.update("FromDateOfDump", fromDateFormat);
        AnalyticService.update("DumpOfDays", days);
        Date fromDate = dateFormat.parse(fromDateFormat);
        log.info("from Date {}", fromDate);
        return PaymentDump.builder().transactions(RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), ITransactionDao.class).getTransactionDailyDump(fromDate, endDate)).build();
    }

    private void putTransactionsOnS3Bucket(List<Transaction> transactions, Calendar cal) {
        if (!transactions.isEmpty()) {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String fileName = PAYMENT_DUMP + dateFormat.format(cal.getTime()) + PAYMENT_TRANSACTION;
            String collectionDump = gson.toJson(transactions);
            AnalyticService.update("RecordsFound", true);
            log.info("Putting userPlanDetails on S3");
            putObjectOnAmazonS3(fileName, collectionDump);
        } else {
            AnalyticService.update("RecordsFound", false);
            log.info("No record found in TransactionDump");
        }
    }

    private void putObjectOnAmazonS3(String fileName, String object) {
        try {
            amazonS3Client.putObject(bucket, fileName, object);
            AnalyticService.update("success", true);
            AnalyticService.update("FileName", fileName);
            log.info("Weekly transaction dump uploaded successfully on S3 at directory: {}", fileName);
        } catch (Exception ex) {
            AnalyticService.update("success", false);
            log.error(AMAZON_SERVICE_ERROR, "Amazon error occurred " + ex.getMessage());
        }
    }

    @ClientAware(clientId = "#clientId")
    @AnalyseTransaction(name = "startPaymentDumpS3Export")
    public void startPaymentDumpS3Export(String requestId, int days, String clientId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("startPaymentDumpS3Export", true);
        log.info("Starting weekly dump s3 export!!");
        Calendar cal = Calendar.getInstance();
        putTransactionDataOnS3(cal, days);
    }

    @ClientAware(clientId = "#clientId")
    @AnalyseTransaction(name = "startPaymentDumpS3Export")
    public void startPaymentDumpS3Export(String requestId, long startTime, String clientId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("startPaymentDumpS3Export", true);
        log.info("Starting weekly dump s3 export!!");
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        putTransactionDataOnS3(cal, 1);
    }

}