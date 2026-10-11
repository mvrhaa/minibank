package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ALICE_NUMBER = "100000000001";
    private static final String BOB_NUMBER = "100000000002";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    private User alice;
    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        alice = new User("alice", "hash", "Alice Example", "alice@example.com");
        User bob = new User("bob", "hash", "Bob Example", "bob@example.com");

        aliceAccount = new Account(ALICE_NUMBER, alice);
        aliceAccount.credit(new BigDecimal("100.00"));

        bobAccount = new Account(BOB_NUMBER, bob);
        bobAccount.credit(new BigDecimal("50.00"));
    }

    @Test
    void openAccountSavesNewAccountWithZeroBalance() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.openAccount("alice");

        assertThat(account.getOwner()).isSameAs(alice);
        assertThat(account.getBalance()).isEqualByComparingTo("0");
        assertThat(account.getAccountNumber()).matches("\\d{12}");
        verify(accountRepository).save(account);
    }

    @Test
    void openAccountGeneratesAnotherNumberWhenFirstIsTaken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true, false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.openAccount("alice");

        verify(accountRepository, times(2)).existsByAccountNumber(anyString());
    }

    @Test
    void openAccountFailsForUnknownUser() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.openAccount("nobody"))
                .isInstanceOf(UserNotFoundException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void getAccountReturnsOwnAccount() {
        when(accountRepository.findByAccountNumber(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        Account account = accountService.getAccount(ALICE_NUMBER, "alice");

        assertThat(account).isSameAs(aliceAccount);
    }

    @Test
    void getAccountFailsWhenAccountDoesNotExist() {
        when(accountRepository.findByAccountNumber(ALICE_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(ALICE_NUMBER, "alice"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getAccountFailsForSomeoneElsesAccount() {
        when(accountRepository.findByAccountNumber(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.getAccount(BOB_NUMBER, "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void getAccountsOfReturnsAccountsFromRepository() {
        when(accountRepository.findByOwnerUsername("alice")).thenReturn(List.of(aliceAccount));

        assertThat(accountService.getAccountsOf("alice")).containsExactly(aliceAccount);
    }

    @Test
    void depositAddsMoney() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        Account account = accountService.deposit(ALICE_NUMBER, new BigDecimal("25.50"), "alice");

        assertThat(account.getBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void depositFailsWhenAccountDoesNotExist() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void depositFailsForSomeoneElsesAccount() {
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.deposit(BOB_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void depositFailsForNegativeAmount() {
        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, new BigDecimal("-10.00"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositFailsForZeroAmount() {
        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, BigDecimal.ZERO, "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositFailsForMissingAmount() {
        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, null, "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositFailsForMoreThanTwoDecimals() {
        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, new BigDecimal("10.555"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositFailsWhenBalanceWouldExceedMaximum() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        assertThatThrownBy(() -> accountService.deposit(ALICE_NUMBER, Account.MAX_BALANCE, "alice"))
                .isInstanceOf(InvalidAmountException.class);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawTakesMoney() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        Account account = accountService.withdraw(ALICE_NUMBER, new BigDecimal("40.00"), "alice");

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawCanEmptyTheAccount() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        Account account = accountService.withdraw(ALICE_NUMBER, new BigDecimal("100.00"), "alice");

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawFailsWithInsufficientFunds() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));

        assertThatThrownBy(() -> accountService.withdraw(ALICE_NUMBER, new BigDecimal("100.01"), "alice"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawFailsForNegativeAmount() {
        assertThatThrownBy(() -> accountService.withdraw(ALICE_NUMBER, new BigDecimal("-1.00"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void withdrawFailsForSomeoneElsesAccount() {
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.withdraw(BOB_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        Account source = accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("30.00"), "alice");

        assertThat(source).isSameAs(aliceAccount);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("70.00");
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void transferFailsWithInsufficientFundsAndChangesNothing() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("100.01"), "alice"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferFailsWhenDestinationDoesNotExist() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferFailsWhenSourceDoesNotExist() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferFailsForSameAccount() {
        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, ALICE_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(SameAccountTransferException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transferFailsForNegativeAmount() {
        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("-10.00"), "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transferFailsForZeroAmount() {
        assertThatThrownBy(() -> accountService.transfer(ALICE_NUMBER, BOB_NUMBER, BigDecimal.ZERO, "alice"))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transferFailsFromSomeoneElsesAccount() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.transfer(BOB_NUMBER, ALICE_NUMBER, new BigDecimal("10.00"), "alice"))
                .isInstanceOf(AccountAccessDeniedException.class);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(bobAccount.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferLocksBothAccountsBeforeChangingBalances() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("10.00"), "alice");

        verify(accountRepository).findByAccountNumberForUpdate(ALICE_NUMBER);
        verify(accountRepository).findByAccountNumberForUpdate(BOB_NUMBER);
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }

    @Test
    void transferLocksAccountsInSameOrderInBothDirections() {
        when(accountRepository.findByAccountNumberForUpdate(ALICE_NUMBER)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate(BOB_NUMBER)).thenReturn(Optional.of(bobAccount));

        accountService.transfer(ALICE_NUMBER, BOB_NUMBER, new BigDecimal("10.00"), "alice");
        accountService.transfer(BOB_NUMBER, ALICE_NUMBER, new BigDecimal("10.00"), "bob");

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByAccountNumberForUpdate(ALICE_NUMBER);
        order.verify(accountRepository).findByAccountNumberForUpdate(BOB_NUMBER);
        order.verify(accountRepository).findByAccountNumberForUpdate(ALICE_NUMBER);
        order.verify(accountRepository).findByAccountNumberForUpdate(BOB_NUMBER);
    }
}
