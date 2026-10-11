package com.minibank.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.service.AccountService;
import com.minibank.web.dto.AccountResponse;
import com.minibank.web.dto.AmountRequest;
import com.minibank.web.dto.OpenAccountRequest;
import com.minibank.web.dto.TransferRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(accountService.openAccount(request.ownerUsername()));
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@PathVariable String accountNumber) {
        return AccountResponse.from(accountService.getAccount(accountNumber));
    }

    @PostMapping("/{accountNumber}/deposit")
    public AccountResponse deposit(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.deposit(accountNumber, request.amount()));
    }

    @PostMapping("/{accountNumber}/withdraw")
    public AccountResponse withdraw(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.withdraw(accountNumber, request.amount()));
    }

    @PostMapping("/transfer")
    public AccountResponse transfer(@Valid @RequestBody TransferRequest request) {
        return AccountResponse.from(accountService.transfer(
                request.fromAccountNumber(), request.toAccountNumber(), request.amount()));
    }
}
