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
import com.minibank.exception.AccountAccessDeniedException;
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
        Account account = accountService.getAccount(alice, "alice");

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
        accountService.deposit(alice, new BigDecimal("100.50"), "alice");
        accountService.withdraw(alice, new BigDecimal("40.25"), "alice");

        assertThat(balanceOf(alice)).isEqualByComparingTo("60.25");
    }

    @Test
    void withdrawFailsWhenBalanceIsTooLow() {
        accountService.deposit(alice, new BigDecimal("50.00"), "alice");

        assertThatThrownBy(() -> accountService.withdraw(alice, new BigDecimal("50.01"), "alice"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("50.00");
    }

    @Test
    void negativeAndZeroAmountsAreRejected() {
        assertThatThrownBy(() -> accountService.deposit(alice, new BigDecimal("-1"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.withdraw(alice, BigDecimal.ZERO, "alice"))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.transfer(alice, bob, new BigDecimal("-5.00"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.deposit(alice, null, "alice"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> accountService.deposit(alice, new BigDecimal("10.999"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void unknownAccountIsRejected() {
        assertThatThrownBy(() -> accountService.getAccount("000000000000", "alice"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> accountService.deposit("000000000000", new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferMovesMoney() {
        accountService.deposit(alice, new BigDecimal("100.00"), "alice");

        accountService.transfer(alice, bob, new BigDecimal("30.00"), "alice");

        assertThat(balanceOf(alice)).isEqualByComparingTo("70.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("30.00");
    }

    @Test
    void transferFailsWhenBalanceIsTooLow() {
        accountService.deposit(alice, new BigDecimal("10.00"), "alice");

        assertThatThrownBy(() -> accountService.transfer(alice, bob, new BigDecimal("10.01"), "alice"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
    }

    @Test
    void transferToUnknownAccountChangesNothing() {
        accountService.deposit(alice, new BigDecimal("100.00"), "alice");

        assertThatThrownBy(() -> accountService.transfer(alice, "000000000000", new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void transferToSameAccountIsRejected() {
        accountService.deposit(alice, new BigDecimal("100.00"), "alice");

        assertThatThrownBy(() -> accountService.transfer(alice, alice, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void userCannotReadSomeoneElsesAccount() {
        assertThatThrownBy(() -> accountService.getAccount(bob, "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void userCannotDepositToOrWithdrawFromSomeoneElsesAccount() {
        accountService.deposit(bob, new BigDecimal("100.00"), "bob");

        assertThatThrownBy(() -> accountService.deposit(bob, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThatThrownBy(() -> accountService.withdraw(bob, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThat(balanceOf(bob)).isEqualByComparingTo("100.00");
    }

    @Test
    void userCannotTransferFromSomeoneElsesAccount() {
        accountService.deposit(bob, new BigDecimal("100.00"), "bob");

        assertThatThrownBy(() -> accountService.transfer(bob, alice, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThat(balanceOf(bob)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(alice)).isEqualByComparingTo("0");
    }

    @Test
    void getAccountsOfReturnsOnlyOwnAccounts() {
        accountService.openAccount("alice");

        assertThat(accountService.getAccountsOf("alice")).hasSize(2);
        assertThat(accountService.getAccountsOf("bob")).hasSize(1);
    }

    @Test
    void concurrentTransfersKeepTotalMoney() throws Exception {
        accountService.deposit(alice, new BigDecimal("1000.00"), "alice");
        accountService.deposit(bob, new BigDecimal("1000.00"), "bob");
        AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 40; i++) {
            executor.submit(() -> {
                try {
                    accountService.transfer(alice, bob, new BigDecimal("7.00"), "alice");
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
            executor.submit(() -> {
                try {
                    accountService.transfer(bob, alice, new BigDecimal("3.00"), "bob");
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
        accountService.deposit(alice, new BigDecimal("100.00"), "alice");
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficientFunds = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    accountService.withdraw(alice, new BigDecimal("10.00"), "alice");
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
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}
