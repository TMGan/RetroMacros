package com.mealplanner.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the tables Spring Session stores sign-ins in, if they don't exist yet.
 *
 * Sessions live in the database (not server memory) so a deploy or restart doesn't
 * sign everyone out. Spring's own setup script uses plain CREATE TABLE, which fails
 * on the second startup against Postgres, so this uses IF NOT EXISTS instead and
 * runs on every startup. Schema matches Spring Session JDBC 3.x.
 */
@Component
public class SessionTableSetup {

    private static final Logger log = LoggerFactory.getLogger(SessionTableSetup.class);

    private static final String[] STATEMENTS = {
            "CREATE TABLE IF NOT EXISTS SPRING_SESSION ("
                    + " PRIMARY_ID CHAR(36) NOT NULL,"
                    + " SESSION_ID CHAR(36) NOT NULL,"
                    + " CREATION_TIME BIGINT NOT NULL,"
                    + " LAST_ACCESS_TIME BIGINT NOT NULL,"
                    + " MAX_INACTIVE_INTERVAL INT NOT NULL,"
                    + " EXPIRY_TIME BIGINT NOT NULL,"
                    + " PRINCIPAL_NAME VARCHAR(100),"
                    + " CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID))",
            "CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)",
            "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)",
            "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)",
            "CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES ("
                    + " SESSION_PRIMARY_ID CHAR(36) NOT NULL,"
                    + " ATTRIBUTE_NAME VARCHAR(200) NOT NULL,"
                    + " ATTRIBUTE_BYTES BYTEA NOT NULL,"
                    + " CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),"
                    + " CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)"
                    + " REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE)"
    };

    private final JdbcTemplate jdbcTemplate;

    public SessionTableSetup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTables() {
        for (String sql : STATEMENTS) {
            jdbcTemplate.execute(sql);
        }
        log.info("Session tables ready");
    }
}
