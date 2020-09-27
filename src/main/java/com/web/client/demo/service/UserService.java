package com.web.client.demo.service;

import com.web.client.demo.model.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {
    private final WebClient webClient;

    public Mono<User> getUserByIdAsync(final String id) {
        return webClient
                .get()
                .uri(String.join("", "/users/", id))
                .retrieve()
                .bodyToMono(User.class);
    }

    public User getUserByIdSync(final String id) {
        return webClient
                .get()
                .uri(String.join("", "/users/", id))
                .retrieve()
                .bodyToMono(User.class)
                .block();
    }

    public User getUserWithRetry(final String id) {
        return webClient
                .get()
                .uri(String.join("", "/broken-url/", id))
                .retrieve()
                .bodyToMono(User.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofMillis(300)))
                .block();
    }

    public User getUserWithFallback(final String id) {
        return webClient
                .get()
                .uri(String.join("", "/broken-url/", id))
                .retrieve()
                .bodyToMono(User.class)
                .doOnError(error -> log.error("An error has occurred {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(new User()))
                .block();
    }

    public User getUserWithErrorHandling(final String id) {
        return webClient
                .get()
                .uri(String.join("", "/broken-url/", id))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError,
                        error -> Mono.error(new RuntimeException("API not found")))
                .onStatus(HttpStatus::is5xxServerError,
                        error -> Mono.error(new RuntimeException("Server is not responding")))
                .bodyToMono(User.class)
                .block();
    }
}
