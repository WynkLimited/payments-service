package in.wynk.payment.scheduler;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentDump;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.ITransactionDao;
import in.wynk.payment.service.IPaymentDumpService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import static in.wynk.logging.BaseLoggingMarkers.MYSQL_ERROR;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_DUMP;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_TRANSACTION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_SERVICE_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.SDK_CLIENT_ERROR;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
@Service
@Slf4j
public class PaymentDumpService implements IPaymentDumpService {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");


    @Autowired
    private AmazonS3 amazonS3Client;

    @Value("${payment.se.s3.bucket}")
    private String bucket;
    private final ITransactionDao transactionDao;
    @Autowired
    private Gson gson;

    public PaymentDumpService(@Qualifier(BeanConstant.TRANSACTION_DAO)ITransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }
    @AnalyseTransaction(name = "putTransactionDataOnS3")
    private void putTransactionDataOnS3(Calendar cal) {
        try {
            PaymentDump paymentDump = getPaymentDbDump();
            AnalyticService.update("TotalRecordsInDump", paymentDump.getTransactions().size());
        try {
             putTransactionsOnS3Bucket(paymentDump.getTransactions(), cal);
        } catch(AmazonServiceException ex) {
            log.error(AMAZON_SERVICE_ERROR,"AmazonServiceException "+ ex.getErrorMessage());
        } catch(SdkClientException e) {
            log.error(SDK_CLIENT_ERROR,"SdkClientException "+e.getMessage());
        }
        } catch(Exception ex) {
            log.error(MYSQL_ERROR,"Unable to load mySql db "+ ex.getMessage());
        }
    }
    @AnalyseTransaction(name = "getPaymentDbDump")
    private PaymentDump getPaymentDbDump() throws ParseException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String fromDateFormat=dateFormat.format(cal.getTime());
        AnalyticService.update("FromDateOfDump",fromDateFormat);
        Date fromDate=dateFormat.parse(fromDateFormat);
        log.info("from Date {}",fromDate);
        return populatePaymentDump(fromDate);

    }
    @AnalyseTransaction(name = "putTransactionsOnS3Bucket")
    private void putTransactionsOnS3Bucket(List<Transaction> transactions, Calendar cal) {
        if(!transactions.isEmpty()) {
            String fileName = PAYMENT_DUMP + dateFormat.format(cal.getTime()) + PAYMENT_TRANSACTION;
            String collectionDump = gson.toJson(transactions);
            AnalyticService.update("RecordsFound", true);
            log.info("Putting userPlanDetails on S3");
            putObjectOnAmazonS3(fileName,collectionDump);
        }
        else{
            AnalyticService.update("RecordsFound", false);
            log.info("No record found in TransactionDump");
        }
    }
    @AnalyseTransaction(name = "putObjectOnAmazonS3")
    private void putObjectOnAmazonS3(String fileName, String object) {
        try {
            amazonS3Client.putObject(bucket,fileName,object);
            AnalyticService.update("putObjectOnAmazonS3", true);
            AnalyticService.update("FileName", fileName);
            log.info("Weekly transaction dump uploaded successfully on S3 at directory: {}", fileName );
        } catch(Exception ex) {
            log.error(AMAZON_SERVICE_ERROR,"Amazon error occurred "+ ex.getMessage());
        }
    }
    @AnalyseTransaction(name = "startPaymentDumpS3Export")
    public void startPaymentDumpS3Export(String requestId) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("startPaymentDumpS3Export", true);
        log.info("Starting weekly dump s3 export!!");
        Calendar cal = Calendar.getInstance();
        putTransactionDataOnS3(cal);
    }

    @Override
    public PaymentDump populatePaymentDump(Date fromDate) {
        return PaymentDump.builder().transactions(transactionDao.getTransactionWeeklyDump(fromDate)).build();
    }
}
