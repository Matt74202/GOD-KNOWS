package com.Eval.NewApp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

@SpringBootApplication(exclude = {HttpClientAutoConfiguration.class,RestClientAutoConfiguration.class})
public class NewAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(NewAppApplication.class, args);
	}

}
