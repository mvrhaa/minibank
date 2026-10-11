package com.minibank.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.minibank.domain.Account;

public record AccountResponse(String accountNumber, BigDecimal balance, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getAccountNumber(), account.getBalance(), account.getCreatedAt());
    }
}
