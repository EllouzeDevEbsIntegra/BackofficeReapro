package com.reapro.achat.config.datasource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.reapro.achat.repositories.b2bnav",
        entityManagerFactoryRef = "b2bnavEntityManagerFactory",
        transactionManagerRef = "b2bnavTransactionManager"
)
public class B2bNavDataSourceConfig {

    @Bean(name = "b2bnavDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.b2bnav")
    public DataSource b2bnavDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "b2bnavEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean b2bnavEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("b2bnavDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.reapro.achat.entities.b2bnav")
                .persistenceUnit("b2bnav")
                .properties(properties)
                .build();
    }

    @Bean(name = "b2bnavTransactionManager")
    public PlatformTransactionManager b2bnavTransactionManager(
            @Qualifier("b2bnavEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
