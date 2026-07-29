package org.asvosonk.security;

import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * F-25: the read-only reporting role {@code asvosonk_reports} must NOT be able
 * to read authentication tables ({@code app_user}, {@code role},
 * {@code permission}, {@code role_permission}) after V15's whitelist, but must
 * still read the business tables it needs.
 *
 * <p>Connects directly as the reporting role (created by V1 with the default
 * dev password) against the Testcontainers database.
 */
@SpringBootTest
class ReportsRolePrivilegeIT extends AbstractIntegrationTest {

    private Connection reportsConnection() throws SQLException {
        // Same JDBC host/port/db as the running container, but the restricted role.
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), "asvosonk_reports", "reports_pwd_change_me");
    }

    @Test
    void reportsRoleCannotReadAuthTables() throws SQLException {
        try (Connection c = reportsConnection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT password_hash FROM app_user"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> c.createStatement().executeQuery("SELECT * FROM role"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> c.createStatement().executeQuery("SELECT * FROM permission"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> c.createStatement().executeQuery("SELECT * FROM role_permission"))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void reportsRoleCanReadBusinessTables() throws SQLException {
        try (Connection c = reportsConnection(); Statement st = c.createStatement()) {
            assertThatCode(() -> {
                st.executeQuery("SELECT * FROM member");
                c.createStatement().executeQuery("SELECT * FROM cashbox_movement");
                c.createStatement().executeQuery("SELECT * FROM global_movement_view");
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void reportsRoleIsReachable() throws SQLException {
        try (Connection c = reportsConnection()) {
            assertThat(c.isValid(5)).isTrue();
        }
    }
}
