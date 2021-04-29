package in.wynk.payment.controller;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponse;
import in.wynk.payment.scheduler.PaymentMysqlDumpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wynk/s2s/v1/mysqldump/")
public class PaymentMysqlDbDumpController {
    @Autowired
    private PaymentMysqlDumpService paymentMysqlDumpService;
    @GetMapping("/transaction")
    @AnalyseTransaction(name = "transactionWeeklyDump")
    public WynkResponse<Boolean> allPlans() {
        WynkResponse<Boolean> response= WynkResponse.<Boolean>builder().body(paymentMysqlDumpService.startCassandraS3Export()).build();
        AnalyticService.update(response);
        return response;
    }
}
