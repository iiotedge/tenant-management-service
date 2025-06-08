package com.iotmining.services.tms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;


@SpringBootApplication(scanBasePackages = {"com.iotmining"})
@EnableCassandraRepositories(basePackages = "com.iotmining.services.tms.repository")
public class TenantManagementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantManagementServiceApplication.class, args);
	}

}
