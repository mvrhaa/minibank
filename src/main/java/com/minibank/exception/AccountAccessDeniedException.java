package com.minibank.exception;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(String accountNumber) {
        super("You do not own account " + accountNumber);
    }
}
