package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.minibank.domain.Account;
import com.minibank.domain.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.SameAccountTransferException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;
import com.minibank.support.PostgresIntegrationTest;

@SpringBootTest
class AccountServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("alice", "hash", "Alice Example", "alice@example.com"));
        userRepository.save(new User("bob", "hash", "Bob Example", "bob@example.com"));
        alice = accountService.openAccount("alice").getAccountNumber();
        bob = accountService.openAccount("bob").getAccountNumber();
    }

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void newAccountHasZeroBalanceAndTwelveDigitNumber() {
        Account account = accountService.getAccount(alice);

        assertThat(account.getAccountNumber()).matches("\\d{12}");
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void openAccountFailsForUnknownUser() {
        assertThatThrownBy(() -> accountService.openAccount("nobody"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void depositAndWithdrawChangeBalance() {
        accountService.deposit(alice, new BigDecimal("100.50"));
        accountService.withdraw(alice, new BigDecimal("40.25"));

        assertThat(balanceOf(alice)).isEqualByComparingTo("60.25");
    }

    @Test
    void withdrawFailsWhenBalanceIsTooLow() {
        accountService.deposit(alice, new BigDecimal("50.00"));

        assertThatThrownBy(() -> accountService.withdraw(alice, new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("50.00");
    }

    @Test
    void negativeAndZeroAmountsAreRejected() {
        assertThatThrownBy(() -> accountService.deposit(alice, new BigDecimal("-1")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.withdraw(alice, BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.transfer(alice, bob, new BigDecimal("-5.00")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.deposit(alice, null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> accountService.deposit(alice, new BigDecimal("10.999")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void unknownAccountIsRejected() {
        assertThatThrownBy(() -> accountService.getAccount("000000000000"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> accountService.deposit("000000000000", new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferMovesMoney() {
        accountService.deposit(alice, new BigDecimal("100.00"));

        accountService.transfer(alice, bob, new BigDecimal("30.00"));

        assertThat(balanceOf(alice)).isEqualByComparingTo("70.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("30.00");
    }

    @Test
    void transferFailsWhenBalanceIsTooLow() {
        accountService.deposit(alice, new BigDecimal("10.00"));

        assertThatThrownBy(() -> accountService.transfer(alice, bob, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
    }

    @Test
    void transferToUnknownAccountChangesNothing() {
        accountService.deposit(alice, new BigDecimal("100.00"));

        assertThatThrownBy(() -> accountService.transfer(alice, "000000000000", new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void transferToSameAccountIsRejected() {
        accountService.deposit(alice, new BigDecimal("100.00"));

        assertThatThrownBy(() -> accountService.transfer(alice, alice, new BigDecimal("10.00")))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void concurrentTransfersKeepTotalMoney() throws Exception {
        accountService.deposit(alice, new BigDecimal("1000.00"));
        accountService.deposit(bob, new BigDecimal("1000.00"));
        AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 40; i++) {
            executor.submit(() -> {
                try {
                    accountService.transfer(alice, bob, new BigDecimal("7.00"));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
            executor.submit(() -> {
                try {
                    accountService.transfer(bob, alice, new BigDecimal("3.00"));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        executor.shutdown();

        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();
        assertThat(balanceOf(alice)).isEqualByComparingTo("840.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("1160.00");
    }

    @Test
    void concurrentWithdrawalsDoNotOverdraw() throws Exception {
        accountService.deposit(alice, new BigDecimal("100.00"));
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficientFunds = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    accountService.withdraw(alice, new BigDecimal("10.00"));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientFunds.incrementAndGet();
                }
            });
        }
        executor.shutdown();

        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(insufficientFunds.get()).isEqualTo(10);
        assertThat(balanceOf(alice)).isEqualByComparingTo("0");
    }

    private BigDecimal balanceOf(String accountNumber) {
        return accountService.getAccount(accountNumber).getBalance();
    }
}
