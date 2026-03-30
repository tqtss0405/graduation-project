package com.poly.graduation_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ServletComponentScan 
@EnableScheduling
@EnableAsync
public class GraduationProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraduationProjectApplication.class, args);
	}

}
