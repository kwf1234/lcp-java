package com.open.lcp.orm.jade.context.spring;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.ListableBeanFactory;

import com.open.lcp.orm.jade.dataaccess.DataSourceFactory;
import com.open.lcp.orm.jade.dataaccess.DataSourceHolder;
import com.open.lcp.orm.jade.statement.StatementMetaData;

/**
 * 
 * @author 王志亮 [qieqie.wang@gmail.com]
 * 
 */
public class SpringDataSourceFactoryDelegate implements DataSourceFactory {

    private ListableBeanFactory beanFactory;

    private DataSourceFactory dataSourceFactory;

    public SpringDataSourceFactoryDelegate(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    
    public DataSourceHolder getHolder(String name){
    	throw new RuntimeException("not support.");
    }
    
    @Override
	public void replaceHolder(String name, DataSource dataSource) {
		throw new RuntimeException("not support.");
	}

    @Override
    public DataSourceHolder getHolder(StatementMetaData metaData, Map<String, Object> runtimeProperties) {
        if (dataSourceFactory == null) {
            ListableBeanFactory beanFactory = this.beanFactory;
            if (beanFactory != null) {
                if (beanFactory.containsBeanDefinition("jade.dataSourceFactory")) {
                    dataSourceFactory = (DataSourceFactory) beanFactory.getBean(
                            "jade.dataSourceFactory", DataSourceFactory.class);
                } else {
                    dataSourceFactory = new SpringDataSourceFactory(beanFactory);
                }
                this.beanFactory = null;
            }
        }
        return dataSourceFactory.getHolder(metaData, runtimeProperties);
    }

}
