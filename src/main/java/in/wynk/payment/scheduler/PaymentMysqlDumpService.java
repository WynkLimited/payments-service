package in.wynk.payment.scheduler;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.google.gson.Gson;
import in.wynk.payment.core.dao.entity.PaymentDbDump;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.service.IMysqlDbDumpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import static in.wynk.logging.BaseLoggingMarkers.MYSQL_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_SERVICE_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.SDK_CLIENT_ERROR;

@Service
@Slf4j
public class PaymentMysqlDumpService {

    public static final String PAYMENT_DUMP = "payment_dump/";
    public static final String PAYMENT_TRANSACTION = "/transaction.json";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private AmazonS3 amazonS3Client;

   // @Value("${payment.se.s3.bucket}")
    private String bucket="wynk-framework-dev";

    @Autowired
    private IMysqlDbDumpService paymentDbDumpService;

    @Autowired
    private Gson gson;

    private void putTransactionDataOnS3(Calendar cal) {
        PaymentDbDump paymentDbDump = PaymentDbDump.builder().build();
        try {
            paymentDbDump = getPaymentDbDump();
        } catch(Exception ex) {
            log.error(MYSQL_ERROR,"Unable to load mySql db "+ ex.getMessage());
            return;
        }
        try {
            putTransactionsOnS3Bucket(paymentDbDump.getTransactions(), cal);
        } catch(AmazonServiceException ex) {
            // amazons3 could not process
            log.error(AMAZON_SERVICE_ERROR,"AmazonServiceException "+ ex.getErrorMessage());
        } catch(SdkClientException e) {
            // amazon s3 could not be contacted
            log.error(SDK_CLIENT_ERROR,"SdkClientException "+e.getMessage());
        }
    }

    private PaymentDbDump getPaymentDbDump() {
        Calendar cal = Calendar.getInstance();
        Date toDate=cal.getTime();
        cal.add(Calendar.DATE, -7);
        Date fromDate=cal.getTime();
        log.info("start Date {}",fromDate);
        log.info("current Date {}", toDate);
        return paymentDbDumpService.populatePaymentDbDump(fromDate,toDate);
    }

    private void putTransactionsOnS3Bucket(List<Transaction> transactions, Calendar cal) {
        if(!transactions.isEmpty()) {
            String fileName = PAYMENT_DUMP + dateFormat.format(cal.getTime()) + PAYMENT_TRANSACTION;
            String collectionDump = gson.toJson(transactions);
            log.info("Putting userPlanDetails on S3");
            putObjectOnAmazonS3(fileName,collectionDump);
        }
    }

    private void putObjectOnAmazonS3(String fileName, String object) {
        try {
            amazonS3Client.putObject(bucket,fileName,object);
        } catch(Exception ex) {
            log.error(AMAZON_SERVICE_ERROR,"Amazon error occurred "+ ex.getMessage());
            throw ex;
        }
    }

    public void startCassandraS3Export() {
        log.info("Starting subscription s3 export!!");
        Calendar cal = Calendar.getInstance();
        putTransactionDataOnS3(cal);
        log.info("Done for today",cal);
    }
}
