package com.dilnur.library_management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;


@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.autoconfigure.exclude=" +
						"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
						"org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
						"org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
		}
)
@Import(TestDisableConfig.class)
class LibraryManagementApplicationTests {

	@Test
	void contextLoads() {}
}