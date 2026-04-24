package com.legent.tracking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.datasource.clickhouse")
public class ClickHouseProperties {

    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.clickhouse.jdbc.ClickHouseDriver";
}
