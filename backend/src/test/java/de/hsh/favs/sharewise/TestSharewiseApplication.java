package de.hsh.favs.sharewise;

import org.springframework.boot.SpringApplication;

public class TestSharewiseApplication {

	public static void main(String[] args) {
		SpringApplication.from(SharewiseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
