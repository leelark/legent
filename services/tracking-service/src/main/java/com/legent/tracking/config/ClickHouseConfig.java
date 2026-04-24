package com.legent.tracking.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {

    @Bean(name = "clickHouseDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.clickhouse")
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
    public DataSource clickHouseDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "clickHouseJdbcTemplate")
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
    public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}
