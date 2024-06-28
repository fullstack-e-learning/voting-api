package net.samitkumar.voting_api;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SpringBootApplication
@RequiredArgsConstructor
public class VotingApiApplication {
	final ReactiveRedisTemplate<String, Vote> redisTemplate;
	static final String VOTE_ID_COOKIE = "vote_id";

	public static void main(String[] args) {
		SpringApplication.run(VotingApiApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction() {
		return RouterFunctions
				.route()
				.path("/vote", builder -> builder
						.POST("", this::vote)
				)
				.build();
	}

	private Mono<ServerResponse> vote(ServerRequest request) {
		//read vote_id cookie from the request, If exists - it will be a 400 back , or else create a new one then send it back along with the response
		if (Objects.isNull(request.cookies().getFirst(VOTE_ID_COOKIE))) {
			return request
					.bodyToMono(Vote.class)
					.map(vote -> new Vote(UUID.randomUUID().toString(), vote.optionId()))
					.map(vote -> redisTemplate.convertAndSend("votes", vote))
					.flatMap(vote -> ServerResponse.ok()
							.cookie(ResponseCookie.from(VOTE_ID_COOKIE, "").build())
							.bodyValue(Map.of("message","SUCCESS"))
					);
		} else {
			return ServerResponse.badRequest().build();
		}
	}


}

record Vote(String id, String optionId) {}