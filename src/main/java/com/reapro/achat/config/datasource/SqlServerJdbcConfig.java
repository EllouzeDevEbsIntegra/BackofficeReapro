package com.reapro.achat.config.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class SqlServerJdbcConfig {

    @Bean(name = "sqlServerJdbcTemplate")
    public NamedParameterJdbcTemplate sqlServerJdbcTemplate(
            @Qualifier("sqlServerDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}