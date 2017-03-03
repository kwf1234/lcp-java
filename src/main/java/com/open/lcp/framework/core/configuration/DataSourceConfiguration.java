package com.open.lcp.framework.core.configuration;

import java.util.Map;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.open.dbs.DBConfig;
import com.open.env.finder.ZKFinder;
import com.open.jade.jade.context.spring.JadeBeanFactoryPostProcessor;
import com.open.jade.jade.dataaccess.DataSourceFactory;
import com.open.jade.jade.dataaccess.DataSourceHolder;
import com.open.jade.jade.statement.StatementMetaData;
import com.open.lcp.ResourceEnum;

@Configuration
public class DataSourceConfiguration {

	// <bean id="jade.dataSource.com.chen.dao"
	// class="org.apache.commons.dbcp.BasicDataSource"
	// destroy-method="close">
	// <property name="driverClassName"
	// value="com.mysql.jdbc.Driver"></property>
	// <property name="url"
	// value="jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&amp;characterEncoding=utf-8"></property>
	// <property name="username" value="test"></property>
	// <property name="password" value="test"></property>
	// <!-- 运行判断连接超时任务的时间间隔，单位为毫秒，默认为-1，即不执行任务。 -->
	// <property name="timeBetweenEvictionRunsMillis"
	// value="3600000"></property>
	// <!-- 连接的超时时间，默认为半小时。 -->
	// <property name="minEvictableIdleTimeMillis" value="3600000"></property>
	// </bean>
	@Bean(name = "jade.dataSourceFactory")
	public DataSourceFactory getDataSource() {
		return new DataSourceFactory() {
			@Override
			public DataSourceHolder getHolder(StatementMetaData metaData, Map<String, Object> attributes) {
				DBConfig dbconfig = ZKFinder.findMysqlMaster(ResourceEnum.mysql_lcpFramework_master.resourceName());
				BasicDataSource ds = new BasicDataSource();
				// ds.setDriverClassName("com.mysql.jdbc.Driver");
				// ds.setUrl("jdbc:mysql://123.57.204.187:3306/lcp?useUnicode=true&amp;characterEncoding=utf-8");
				// ds.setUsername("root");
				// ds.setPassword("111111");
				ds.setDriverClassName(dbconfig.getDriverClassName());
				ds.setUrl(dbconfig.getUrl());
				ds.setUsername(dbconfig.getUserName());
				ds.setPassword(dbconfig.getPassword());
				ds.setTimeBetweenEvictionRunsMillis(3600000);
				ds.setMinEvictableIdleTimeMillis(3600000);

				DataSourceHolder dataSourceHolder = new DataSourceHolder(ds);
				return dataSourceHolder;
			}
		};
	}

	@Bean
	public JadeBeanFactoryPostProcessor getJadeBeanFactoryPostProcessor() {
		return new JadeBeanFactoryPostProcessor();
	}
}
