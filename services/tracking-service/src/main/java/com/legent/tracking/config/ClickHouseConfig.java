package com.legent.tracking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {

    @Bean(name = "clickHouseJdbcTemplate")
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
    public JdbcTemplate clickHouseJdbcTemplate(ClickHouseProperties properties) {
        DataSource clickHouseDataSource = DataSourceBuilder.create()
                .driverClassName(properties.getDriverClassName())
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
        return new JdbcTemplate(clickHouseDataSource);
    }
}
