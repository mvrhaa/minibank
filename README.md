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

| Method | Path                                     | Body                                               |
|--------|------------------------------------------|----------------------------------------------------|
| POST   | `/api/accounts`                          | `{"ownerUsername"}`                                |
| GET    | `/api/accounts/{accountNumber}`          |                                                    |
| POST   | `/api/accounts/{accountNumber}/deposit`  | `{"amount"}`                                       |
| POST   | `/api/accounts/{accountNumber}/withdraw` | `{"amount"}`                                       |
| POST   | `/api/accounts/transfer`                 | `{"fromAccountNumber","toAccountNumber","amount"}` |

## Tests

```sh
./mvnw verify
```

The tests start their own embedded PostgreSQL, so no database is needed to run them.
