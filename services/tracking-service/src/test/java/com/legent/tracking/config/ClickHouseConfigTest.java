package com.legent.tracking.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClickHouseConfigTest {

    @Test
    void jdbcTemplateBean_UsesPrimaryPostgresDataSource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            DataSource postgresDataSource = mock(DataSource.class);
            context.registerBean(DataSource.class, () -> postgresDataSource);
            context.register(ClickHouseConfig.class);

            context.refresh();

            JdbcTemplate jdbcTemplate = context.getBean("jdbcTemplate", JdbcTemplate.class);
            assertThat(jdbcTemplate.getDataSource()).isSameAs(postgresDataSource);
        }
    }
}
