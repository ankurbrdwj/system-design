package com.ankur.design.lld.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Setup;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerServiceTest {
    LedgerService service ;

    @BeforeEach
void setUp(){
    service = new LedgerService();
}
    @Test
    void createAccount_returnsPositiveId() {
        Currency ccy= Currency.getInstance("USD");
        long id = service.createAccount(ccy);
        assertTrue(id > 0);
    }

    @Test
    void getBalance_onNewAccount_returnsZero() {
       long id= service.createAccount(Currency.getInstance("USD"));
         BigDecimal amount = service.getBalance(id);
        assertTrue(amount.equals(BigDecimal.ZERO));
    }
    @Test
    void testTransferFunds()
    {
        LedgerService service = new LedgerService();
        long from = service.createAccount(Currency.getInstance("USD"));
        long to   = service.createAccount(Currency.getInstance("USD"));
        BigDecimal amount = new BigDecimal(5000);
        from = service.deposit(from,amount);
        service.transferFunds(from, to, amount, Currency.getInstance("USD"));
        assertEquals(new BigDecimal("0"),   service.getBalance(from));
        assertEquals(new BigDecimal("5000"), service.getBalance(to));
    }
}