package com.minibank.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minibank.domain.Account;
import com.minibank.domain.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.service.AccountService;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    private static final String ALICE = "100000000001";
    private static final String BOB = "100000000002";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void openAccountReturnsCreated() throws Exception {
        when(accountService.openAccount("alice")).thenReturn(account(ALICE, "0.00"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerUsername\": \"alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(ALICE));
    }

    @Test
    void openAccountWithBlankOwnerReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerUsername\": \"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountReturnsBalance() throws Exception {
        when(accountService.getAccount(ALICE)).thenReturn(account(ALICE, "42.50"));

        mockMvc.perform(get("/api/accounts/" + ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(42.50));
    }

    @Test
    void getUnknownAccountReturnsNotFound() throws Exception {
        when(accountService.getAccount(ALICE)).thenThrow(new AccountNotFoundException(ALICE));

        mockMvc.perform(get("/api/accounts/" + ALICE))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositReturnsNewBalance() throws Exception {
        when(accountService.deposit(ALICE, new BigDecimal("25.00"))).thenReturn(account(ALICE, "25.00"));

        mockMvc.perform(post("/api/accounts/" + ALICE + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.00));
    }

    @Test
    void depositWithNegativeAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/accounts/" + ALICE + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void withdrawWithInsufficientFundsReturnsBadRequest() throws Exception {
        when(accountService.withdraw(any(), any())).thenThrow(new InsufficientFundsException(ALICE));

        mockMvc.perform(post("/api/accounts/" + ALICE + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferReturnsSourceAccount() throws Exception {
        when(accountService.transfer(ALICE, BOB, new BigDecimal("30.00"))).thenReturn(account(ALICE, "70.00"));

        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("30.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ALICE))
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    void transferWithNegativeAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("-30.00")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void transferToUnknownAccountReturnsNotFound() throws Exception {
        when(accountService.transfer(any(), any(), any())).thenThrow(new AccountNotFoundException(BOB));

        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson("30.00")))
                .andExpect(status().isNotFound());
    }

    private String transferJson(String amount) {
        return "{\"fromAccountNumber\": \"" + ALICE + "\", \"toAccountNumber\": \"" + BOB
                + "\", \"amount\": " + amount + "}";
    }

    private Account account(String accountNumber, String balance) {
        Account account = new Account(accountNumber, new User("alice", "hash", "Alice Example", "alice@example.com"));
        account.credit(new BigDecimal(balance));
        return account;
    }
}
