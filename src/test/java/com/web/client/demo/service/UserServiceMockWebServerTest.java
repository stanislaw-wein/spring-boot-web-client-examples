package com.web.client.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.client.demo.model.User;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class UserServiceMockWebServerTest {

  private static MockWebServer mockWebServer;
  private static UserService userService;
  private static ObjectMapper objectMapper;

  // Set up the test environment by initializing the mock web server and user service
  @BeforeAll
  static void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    userService = new UserService(WebClient.builder()
        .baseUrl(String.format("http://localhost:%s", mockWebServer.getPort()))
        .build());
  }

  // Tear down the test environment by shutting down the mock web server
  @AfterAll
  static void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  // Test the asynchronous getUserById method
  @Test
  void whenGetUserByIdAsync_thenShouldReturnUser() throws JsonProcessingException {
    User expectedUser = new User(1, "Eric Cartman", "eric.cartman@email.com");
    mockWebServer.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(expectedUser))
        .addHeader("Content-Type", "application/json"));

    Mono<User> userMono = userService.getUserByIdAsync("1");

    StepVerifier.create(userMono).thenAwait().expectNextMatches(user -> user.equals(expectedUser))
        .verifyComplete();
  }

  // Test the synchronous getUserById method
  @Test
  void whenGetUserByIdSync_thenShouldReturnUser()
      throws JsonProcessingException, InterruptedException {
    User expectedUser = new User(1, "Eric Cartman", "eric.cartman@email.com");
    mockWebServer.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(expectedUser))
        .addHeader("Content-Type", "application/json"));

    User actualUser = userService.getUserByIdSync("1");
    RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);

    assertThat(request).isNotNull();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/users/".concat(String.valueOf(expectedUser.getId())));
    assertThat(actualUser).isEqualTo(expectedUser);
  }

  // Test the getUserWithRetryAsync method when the API keeps failing
  @Test
  void whenGetUserWithRetryAsync_apiKeepsFailing_thenShouldReturnError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(418));
    mockWebServer.enqueue(new MockResponse().setResponseCode(418));
    mockWebServer.enqueue(new MockResponse().setResponseCode(418));
    mockWebServer.enqueue(new MockResponse().setResponseCode(418));

    Mono<User> userMono = userService.getUserWithRetryAsync("1");

    StepVerifier.create(userMono)
        .expectErrorMatches(throwable -> throwable.getMessage()
            .equals("Retries exhausted: 3/3") && throwable.getCause()
            .getMessage()
            .contains("Something went wrong: 418 I'm a teapot"))
        .verify();
  }

  // Test the getUserWithRetryAsync method when the API eventually succeeds
  @Test
  void whenGetUserWithRetryAsync_apiEventuallySucceeds_thenShouldReturnUser()
      throws JsonProcessingException {
    User expectedUser = new User(1, "Eric Cartman", "eric.cartman@email.com");
// Enqueue two failed responses and then a successful response
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    mockWebServer.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(expectedUser))
        .addHeader("Content-Type", "application/json"));

    Mono<User> userMono = userService.getUserWithRetryAsync("1");

    StepVerifier.create(userMono).expectNextMatches(user -> user.equals(expectedUser))
        .verifyComplete();
  }

  // Test the getUserWithFallback method when the API fails
  @Test
  void whenGetUserWithFallback_thenShouldReturnFallbackUser() {
    User fallbackUser = new User();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    User actualUser = userService.getUserWithFallback("1");

    assertThat(actualUser).isEqualTo(fallbackUser);
  }

  // Test the getUserWithErrorHandling method when the API returns a Not Found error
  @Test
  void whenGetUserWithErrorHandling_notFound_thenShouldReturnError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));
    Exception exception = assertThrows(RuntimeException.class,
        () -> userService.getUserWithErrorHandling("1"));

    assertThat(exception.getMessage()).isEqualTo("API not found");
  }

  // Test the getUserWithErrorHandling method when the API returns a Service Unavailable error
  @Test
  void whenGetUserWithErrorHandling_serviceUnavailable_thenShouldReturnError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(503));
    Exception exception = assertThrows(RuntimeException.class,
        () -> userService.getUserWithErrorHandling("1"));

    assertThat(exception.getMessage()).isEqualTo("Server is not responding");
  }
}