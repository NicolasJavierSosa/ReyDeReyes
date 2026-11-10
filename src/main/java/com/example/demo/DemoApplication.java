package com.example.demo;

import java.util.Arrays;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.demo.sync.MigradorNube;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class DemoApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
		if (Arrays.asList(args).contains("--migrar-nube")) {
			// Prepara o actualiza la base de Supabase y termina (no levanta el sistema)
			MigradorNube.ejecutar();
			return;
		}
		SpringApplication.run(DemoApplication.class, args);
	}
}
