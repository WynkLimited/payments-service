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
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.logging.BaseLoggingMarkers.MYSQL_ERROR;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_DUMP;
import static in.wynk.payment.core.constant.PaymentDumpConstants.PAYMENT_TRANSACTION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_SERVICE_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.SDK_CLIENT_ERROR;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
@Service
@Transactional
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

    private void putTransactionDataOnS3(Calendar cal, int days) {
        try {
            List<Transaction> transactionList = getPaymentDbDump(days).getTransactions().collect(Collectors.toList());
            AnalyticService.update("TotalRecordsInDump", transactionList.size());
            try {
                putTransactionsOnS3Bucket(transactionList, cal);
            } catch(AmazonServiceException ex) {
                AnalyticService.update(AMAZON_SERVICE_ERROR.getName(),ex.getErrorMessage());
                log.error(AMAZON_SERVICE_ERROR,"AmazonServiceException "+ ex.getErrorMessage());
            } catch(SdkClientException e) {
                AnalyticService.update(SDK_CLIENT_ERROR.getName(),e.getMessage());
                log.error(SDK_CLIENT_ERROR,"SdkClientException "+e.getMessage());
            }
        } catch(Exception ex) {
            AnalyticService.update(MYSQL_ERROR.getName(),ex.getMessage());
            log.error(MYSQL_ERROR,"Unable to load mySql db "+ ex.getMessage());
        }
    }

    private PaymentDump getPaymentDbDump(int days) throws ParseException {
        Calendar cal = Calendar.getInstance();
        // cal.add(Calendar.DATE, -7);
        cal.add(Calendar.DATE, -days);
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String fromDateFormat=dateFormat.format(cal.getTime());
        AnalyticService.update("FromDateOfDump",fromDateFormat);
        AnalyticService.update("DumpOfDays",days);
        Date fromDate=dateFormat.parse(fromDateFormat);
        log.info("from Date {}",fromDate);
        return populatePaymentDump(fromDate);

    }

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

    private void putObjectOnAmazonS3(String fileName, String object) {
        try {
            amazonS3Client.putObject(bucket,fileName,object);
            AnalyticService.update("success", true);
            AnalyticService.update("FileName", fileName);
            log.info("Weekly transaction dump uploaded successfully on S3 at directory: {}", fileName );
        } catch(Exception ex) {
            AnalyticService.update("success", false);
            log.error(AMAZON_SERVICE_ERROR,"Amazon error occurred "+ ex.getMessage());
        }
    }
    @AnalyseTransaction(name = "startPaymentDumpS3Export")
    public void startPaymentDumpS3Export(String requestId, int days) {
        MDC.put(REQUEST_ID, requestId);
        AnalyticService.update(REQUEST_ID, requestId);
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("startPaymentDumpS3Export", true);
        log.info("Starting weekly dump s3 export!!");
        Calendar cal = Calendar.getInstance();
        putTransactionDataOnS3(cal,days);
    }

    @Override
    public PaymentDump populatePaymentDump(Date fromDate) {
        return PaymentDump.builder().transactions(transactionDao.getTransactionWeeklyDump(fromDate)).build();
    }
}
