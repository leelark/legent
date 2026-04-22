package com.legent.tracking.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {

    @Bean(name = "clickHouseDataSource")
    public DataSource clickHouseDataSource(Environment environment) {
        String url = firstNonBlank(
                environment.getProperty("spring.datasource.clickhouse.url"),
                environment.getProperty("spring.datasource.url")
        );
        String username = firstNonBlank(
                environment.getProperty("spring.datasource.clickhouse.username"),
                environment.getProperty("spring.datasource.username")
        );
        String password = firstNonBlank(
                environment.getProperty("spring.datasource.clickhouse.password"),
                environment.getProperty("spring.datasource.password")
        );
        String driverClassName = firstNonBlank(
                environment.getProperty("spring.datasource.clickhouse.driver-class-name"),
                environment.getProperty("spring.datasource.driver-class-name")
        );

        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        if (StringUtils.hasText(driverClassName)) {
            builder.driverClassName(driverClassName);
        }
        return builder
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }
}
