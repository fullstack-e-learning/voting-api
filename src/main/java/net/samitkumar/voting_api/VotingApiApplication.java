package net.samitkumar.voting_api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
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
	final ReactiveRedisTemplate<String, Vote> reactiveRedisTemplate;

	static final String VOTE_ID_COOKIE = "vote_id";

	@Value("${spring.data.redis.channel-name}")
	private String channelName;

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
		return Mono.justOrEmpty(request.cookies().getFirst(VOTE_ID_COOKIE))
				.flatMap(cookie -> ServerResponse.badRequest().build())
				.switchIfEmpty(
						request.bodyToMono(Vote.class)
								.map(vote -> new Vote(UUID.randomUUID().toString(), vote.optionId())) // Create a new Vote
								.flatMap(vote ->
										reactiveRedisTemplate.convertAndSend(channelName, vote)
										.flatMap(redisReplyId ->
												ServerResponse
														.ok()
														.cookie(ResponseCookie.from(VOTE_ID_COOKIE, vote.id()).build())
														.bodyValue(Map.of("message","SUCCESS"))
										)
								)
				);
	}
}

record Vote(String id, String optionId) {}

@Configuration
class RedisConfig {

	@Bean
	public ReactiveRedisTemplate<String, Vote> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {

		Jackson2JsonRedisSerializer<Vote> serializer = new Jackson2JsonRedisSerializer<>(Vote.class);
		RedisSerializationContext.RedisSerializationContextBuilder<String, Vote> builder =
				RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
		RedisSerializationContext<String, Vote> context = builder.value(serializer).build();
		return new ReactiveRedisTemplate<>(factory, context);

		/*
		//OR
		RedisSerializationContext<String, Vote> serializationContext = RedisSerializationContext
				.<String, Vote>newSerializationContext(new StringRedisSerializer())
				.key(new StringRedisSerializer())
				.value(new Jackson2JsonRedisSerializer<>(Vote.class))
				.hashKey(new Jackson2JsonRedisSerializer<>(String.class))
				.hashValue(new Jackson2JsonRedisSerializer<>(Vote.class))
				.build();

		return new ReactiveRedisTemplate<>(factory, serializationContext);*/

	}
}