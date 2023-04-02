package com.web.client.demo.service;

import static com.web.client.demo.service.UserService.BROKEN_URL_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.web.client.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserServiceMockitoTest {

  @Mock
  private WebClient webClient;

  @Mock
  private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @Mock
  private WebClient.RequestHeadersSpec requestHeadersSpec;

  @Mock
  private WebClient.ResponseSpec responseSpec;

  @InjectMocks
  private UserService userService;

  // Test the getUserByIdAsync method using Mockito
  @Test
  void whenGetUserByIdAsync_thenShouldReturnUser() {
    User expectedUser = new User(1, "Eric Cartman", "eric.cartman@email.com");
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(eq(UserService.USERS_URL_TEMPLATE), eq("1")))
        .thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.just(expectedUser));

    Mono<User> userMono = userService.getUserByIdAsync("1");

    assertThat(userMono.block()).isEqualTo(expectedUser);
  }

  // Test the getUserByIdSync method using Mockito
  @Test
  void whenGetUserByIdSync_thenShouldReturnUser() {
    User expectedUser = new User(1, "Eric Cartman", "eric.cartman@email.com");
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(eq(UserService.USERS_URL_TEMPLATE), eq("1")))
        .thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.just(expectedUser));

    User actualUser = userService.getUserByIdSync("1");

    assertThat(actualUser).isEqualTo(expectedUser);
  }

  // Test the getUserWithRetryAsync method when the API keeps failing
  @Test
  void whenGetUserWithRetryAsync_apiKeepsFailing_thenShouldReturnError() {
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(eq(BROKEN_URL_TEMPLATE), eq("1")))
        .thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(User.class)).thenReturn(
        Mono.error(new RuntimeException("API is down")));

    Throwable throwable = catchThrowable(() -> userService.getUserWithRetryAsync("1").block());
    assertThat(throwable).isInstanceOf(RuntimeException.class)
        .hasMessage("Retries exhausted: 3/3");
    assertThat(throwable.getCause()).isInstanceOf(RuntimeException.class)
        .hasMessage("Something went wrong: API is down");
  }

  // Test the getUserWithFallback method when the API fails
  @Test
  void whenGetUserWithFallback_thenShouldReturnFallbackUser() {
    User fallbackUser = new User();
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(eq(UserService.BROKEN_URL_TEMPLATE), eq("1")))
        .thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.error(
        WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
  }
}