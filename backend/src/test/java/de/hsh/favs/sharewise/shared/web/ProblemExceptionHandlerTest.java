package de.hsh.favs.sharewise.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ProblemExceptionHandlerTest {

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ProblemExceptionHandler()).build();
	}

	@Test
	void mapsDomainExceptionToProblemJson() throws Exception {
		mvc.perform(get("/boom"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"))
			.andExpect(jsonPath("$.detail").value("not yours"));
	}

	@Test
	void malformedUuidReturns400BadRequest() throws Exception {
		mvc.perform(get("/things/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void unreadableJsonBodyReturns400BadRequest() throws Exception {
		mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@RestController
	static class Probe {

		@GetMapping("/boom")
		String boom() {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "not yours");
		}

		@GetMapping("/things/{id}")
		String thing(@PathVariable UUID id) {
			return id.toString();
		}

		@PostMapping("/things")
		String create(@RequestBody Body body) {
			return "ok";
		}

		record Body(String name) {
		}

	}

}
