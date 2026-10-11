package com.minibank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.minibank.domain.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;
import com.minibank.service.AccountService;
import com.minibank.service.UserService;
import com.minibank.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String aliceAccount;
    private String bobAccount;

    @BeforeEach
    void setUp() {
        userService.register("alice", PASSWORD, "Alice Example", "alice@example.com");
        userService.register("bob", PASSWORD, "Bob Example", "bob@example.com");
        aliceAccount = accountService.openAccount("alice").getAccountNumber();
        bobAccount = accountService.openAccount("bob").getAccountNumber();
        accountService.deposit(aliceAccount, new BigDecimal("100.00"), "alice");
        accountService.deposit(bobAccount, new BigDecimal("100.00"), "bob");
    }

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUserWithHashedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("carol", "carol@example.com", "secret1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User carol = userRepository.findByUsername("carol").orElseThrow();
        assertThat(carol.getPasswordHash()).isNotEqualTo("secret1234");
        assertThat(carol.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("secret1234", carol.getPasswordHash())).isTrue();
    }

    @Test
    void registerWithTakenUsernameReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", "new@example.com", "secret1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void registerWithTakenEmailReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("carol", "alice@example.com", "secret1234")))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithShortPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("carol", "carol@example.com", "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Username or password is missing or incorrect"))
                .andExpect(jsonPath("$.path").value("/api/accounts"));
        mockMvc.perform(get("/api/accounts/" + aliceAccount))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts").with(httpBasic("alice", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanOpenAndListOwnAccounts() throws Exception {
        mockMvc.perform(post("/api/accounts").with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/accounts").with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void userCanReadOwnAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/" + aliceAccount).with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void userCannotReadSomeoneElsesAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/" + bobAccount).with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanTransferFromOwnAccount() throws Exception {
        mockMvc.perform(post("/api/accounts/transfer").with(httpBasic("alice", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(aliceAccount, bobAccount, "30.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.00));
    }

    @Test
    void userCannotTransferFromSomeoneElsesAccount() throws Exception {
        mockMvc.perform(post("/api/accounts/transfer").with(httpBasic("alice", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(bobAccount, aliceAccount, "30.00")))
                .andExpect(status().isForbidden());

        BigDecimal bobBalance = accountRepository.findByAccountNumber(bobAccount).orElseThrow().getBalance();
        assertThat(bobBalance).isEqualByComparingTo("100.00");
    }

    @Test
    void userCannotWithdrawFromSomeoneElsesAccount() throws Exception {
        mockMvc.perform(post("/api/accounts/" + bobAccount + "/withdraw").with(httpBasic("alice", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerWithSameUsernameInDifferentCaseReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("ALICE", "new@example.com", "secret1234")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginIgnoresUsernameCase() throws Exception {
        mockMvc.perform(get("/api/accounts/" + aliceAccount).with(httpBasic("ALICE", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void depositAboveMaximumBalanceReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/accounts/" + aliceAccount + "/deposit").with(httpBasic("alice", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 99999999999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownUrlReturnsNotFoundInStandardFormat() throws Exception {
        mockMvc.perform(get("/api/nothing").with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/nothing"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedInStandardFormat() throws Exception {
        mockMvc.perform(delete("/api/accounts/" + aliceAccount).with(httpBasic("alice", PASSWORD)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void webPageIsOpenWithoutLogin() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>MiniBank</title>")));
    }

    @Test
    void unauthorizedResponseAsksBrowserForLoginExceptForWebPageRequests() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));

        mockMvc.perform(get("/api/accounts").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    private String registerJson(String username, String email, String password) {
        return "{\"username\": \"" + username + "\", \"password\": \"" + password
                + "\", \"fullName\": \"Test User\", \"email\": \"" + email + "\"}";
    }

    private String transferJson(String from, String to, String amount) {
        return "{\"fromAccountNumber\": \"" + from + "\", \"toAccountNumber\": \"" + to
                + "\", \"amount\": " + amount + "}";
    }
}
