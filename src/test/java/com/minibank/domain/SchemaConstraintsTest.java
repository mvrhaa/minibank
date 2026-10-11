package com.minibank.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.minibank.support.PostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaConstraintsTest extends PostgresIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void savesUserAndAccount() {
        User owner = em.persistFlushFind(newUser("alice"));
        Account account = em.persistFlushFind(new Account("100000000001", owner));

        assertThat(owner.getId()).isNotNull();
        assertThat(owner.getCreatedAt()).isNotNull();
        assertThat(account.getId()).isNotNull();
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void rejectsDuplicateUsername() {
        em.persistAndFlush(newUser("alice"));

        User duplicate = new User("alice", "hash", "Other Alice", "other@example.com");

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .hasMessageContaining("uq_users_username");
    }

    @Test
    void rejectsDuplicateAccountNumber() {
        User owner = em.persistAndFlush(newUser("alice"));
        em.persistAndFlush(new Account("100000000001", owner));

        assertThatThrownBy(() -> em.persistAndFlush(new Account("100000000001", owner)))
                .hasMessageContaining("uq_accounts_account_number");
    }

    @Test
    void rejectsNegativeBalance() {
        User owner = em.persistAndFlush(newUser("alice"));
        Account account = em.persistAndFlush(new Account("100000000001", owner));

        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("UPDATE accounts SET balance = -0.01 WHERE id = :id")
                .setParameter("id", account.getId())
                .executeUpdate())
                .hasMessageContaining("chk_accounts_balance_non_negative");
    }

    private User newUser(String username) {
        return new User(username, "hash", "Alice Example", username + "@example.com");
    }
}
