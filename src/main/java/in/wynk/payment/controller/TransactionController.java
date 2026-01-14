package in.wynk.payment.controller;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.service.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/s2s")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/transactions")
    public List<Transaction> getLatestTransactions(
            @RequestParam("uid") String uid,
            @RequestParam("clientAlias") String clientAlias,
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        return transactionService.getLatestTransactions(uid, clientAlias, limit);
    }
}
