package com.example.consensus.tenant;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wraps the auto-configured DataSource (named "dataSource") with TenantSchemaDataSource
 * so every connection executes SET search_path before use — no replacement of auto-config.
 */
@Component
public class DataSourceWrapper implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("dataSource".equals(beanName) && bean instanceof DataSource ds) {
            return new TenantSchemaDataSource(ds);
        }
        return bean;
    }
}
