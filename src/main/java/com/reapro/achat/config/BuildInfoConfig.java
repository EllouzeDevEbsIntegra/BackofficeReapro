package com.reapro.achat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

@Configuration
public class BuildInfoConfig {

    @Bean
    @ConditionalOnMissingBean(BuildProperties.class)
    public BuildProperties buildProperties() {
        try {
            ClassPathResource resource = new ClassPathResource("META-INF/build-info.properties");
            if (resource.exists()) {
                Properties props = PropertiesLoaderUtils.loadProperties(resource);
                // Nettoyage des clés : build.version -> version
                Properties cleanProps = new Properties();
                props.forEach((k, v) -> {
                    String key = k.toString();
                    if (key.startsWith("build.")) {
                        cleanProps.put(key.substring(6), v);
                    } else {
                        cleanProps.put(key, v);
                    }
                });
                return new BuildProperties(cleanProps);
            }
        } catch (Exception e) {
            // ignore
        }
        return new BuildProperties(new Properties());
    }

    @Bean
    @ConditionalOnMissingBean(GitProperties.class)
    public GitProperties gitProperties() {
        try {
            ClassPathResource resource = new ClassPathResource("git.properties");
            if (resource.exists()) {
                Properties props = PropertiesLoaderUtils.loadProperties(resource);
                return new GitProperties(props);
            }
        } catch (Exception e) {
            // ignore
        }
        return new GitProperties(new Properties());
    }
}
