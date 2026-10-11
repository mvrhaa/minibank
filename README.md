# MiniBank

A banking REST API built with Java 17, Spring Boot, Spring Data JPA, Spring Security and PostgreSQL.

## Running locally

You need JDK 17 or newer and a PostgreSQL database. The included `compose.yaml` starts one with Docker:

```sh
docker compose up -d
./mvnw spring-boot:run
```

The connection can be changed with the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables.

## API

Register first, then send your username and password with every request using HTTP Basic authentication.

| Method | Path                                     | Body                                               | Login |
|--------|------------------------------------------|----------------------------------------------------|-------|
| POST   | `/api/auth/register`                     | `{"username","password","fullName","email"}`       | no    |
| POST   | `/api/accounts`                          |                                                    | yes   |
| GET    | `/api/accounts`                          |                                                    | yes   |
| GET    | `/api/accounts/{accountNumber}`          |                                                    | yes   |
| POST   | `/api/accounts/{accountNumber}/deposit`  | `{"amount"}`                                       | yes   |
| POST   | `/api/accounts/{accountNumber}/withdraw` | `{"amount"}`                                       | yes   |
| POST   | `/api/accounts/transfer`                 | `{"fromAccountNumber","toAccountNumber","amount"}` | yes   |

Passwords are stored as BCrypt hashes. A user can only read, deposit to, withdraw from and transfer out of their own accounts.

```sh
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","fullName":"Alice","email":"alice@example.com"}'
curl -u alice:password123 -X POST localhost:8080/api/accounts
```

## Tests

```sh
./mvnw verify
```

The tests start their own embedded PostgreSQL, so no database is needed to run them.
