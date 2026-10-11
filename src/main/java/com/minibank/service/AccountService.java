package com.minibank.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.domain.Account;
import com.minibank.domain.User;
import com.minibank.exception.AccountAccessDeniedException;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.SameAccountTransferException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account openAccount(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return accountRepository.save(new Account(generateAccountNumber(), owner));
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsOf(String username) {
        return accountRepository.findByOwnerUsername(username);
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber, String username) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        checkOwner(account, username);
        return account;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account deposit(String accountNumber, BigDecimal amount, String username) {
        validateAmount(amount);
        Account account = findAndLock(accountNumber);
        checkOwner(account, username);
        account.credit(amount);
        return account;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account withdraw(String accountNumber, BigDecimal amount, String username) {
        validateAmount(amount);
        Account account = findAndLock(accountNumber);
        checkOwner(account, username);
        account.debit(amount);
        return account;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, String username) {
        validateAmount(amount);
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new SameAccountTransferException();
        }

        Account from;
        Account to;
        if (fromAccountNumber.compareTo(toAccountNumber) < 0) {
            from = findAndLock(fromAccountNumber);
            to = findAndLock(toAccountNumber);
        } else {
            to = findAndLock(toAccountNumber);
            from = findAndLock(fromAccountNumber);
        }

        checkOwner(from, username);
        from.debit(amount);
        to.credit(amount);
        return from;
    }

    private Account findAndLock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private void checkOwner(Account account, String username) {
        if (!account.getOwner().getUsername().equals(username)) {
            throw new AccountAccessDeniedException(account.getAccountNumber());
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
    }

    private String generateAccountNumber() {
        String number;
        do {
            long digits = ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L);
            number = String.valueOf(digits);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
