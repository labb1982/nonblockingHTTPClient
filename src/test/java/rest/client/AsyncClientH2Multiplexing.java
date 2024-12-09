/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package rest.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorConfig.Builder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import rest.AsyncClientConnectionConfig.RESTClientFailedResponse;
import rest.AsyncClientConnectionConfig.RESTClientSuccessResponse;

@SpringBootTest(classes = AsyncClientH2WOMultiplexing.class)
class AsyncClientH2WOMultiplexing {

	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncClientH2WOMultiplexing.class);

	private static final String HOST = "localhost";
	private static final int PORT = Integer.getInteger("port", 8093);
	private static final String URI = // "http://httpbin.org/post" ;
			"http://" + HOST + ":" + PORT + "/hello";

	// public static void main(final String[] args) throws Exception {

	private static ClientAndServer clientAndServer;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() {
		clientAndServer = ClientAndServer.startClientAndServer(PORT);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterAll
	static void tearDownAfterClass() {
		if (clientAndServer != null) {
			clientAndServer.stop();
		}
	}

	@Test
	void test() {

		// REST API stub
		if (clientAndServer != null) {
			clientAndServer.when(HttpRequest.request().withMethod("POST").withPath("/hello"))
					.respond(HttpResponse.response().withStatusCode(200).withBody("Hello, World!"));
		}

		final Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver = route -> {
			// Use different settings for all secure (TLS) connections
//			final HttpHost targetHost = route.getTargetHost();
//			LOGGER.trace("targetHost:{}", targetHost);
			Timeout connectTimeoutInSeconds = Timeout.of(1, TimeUnit.SECONDS);
			Timeout socketTimeoutInSeconds = Timeout.of(1, TimeUnit.SECONDS);
			TimeValue validationAfterInactivity = TimeValue.of(1, TimeUnit.SECONDS);
			TimeValue ttlInSeconds = TimeValue.ofSeconds(3);
			// if (route.isSecure()) {
			return ConnectionConfig.custom().setConnectTimeout(connectTimeoutInSeconds)
					.setSocketTimeout(socketTimeoutInSeconds).setValidateAfterInactivity(validationAfterInactivity)
					.setTimeToLive(ttlInSeconds).build();
//			} else {
//				return ConnectionConfig.custom().setConnectTimeout(connectTimeoutInSeconds)
//						.setSocketTimeout(socketTimeoutInSeconds).setValidateAfterInactivity(validationAfterInactivity)
//						.setTimeToLive(ttlInSeconds).build();
//			}
		};

		final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
				.create().setDefaultTlsConfig(TlsConfig.custom()//
						.setVersionPolicy(HttpVersionPolicy.NEGOTIATE).build())//
				.setConnectionConfigResolver(connectionConfigResolver)
//                .setMessageMultiplexing(true)
				.build();

		LOGGER.info("defaultMaxPerRoute:{}", connectionManager.getDefaultMaxPerRoute());
		connectionManager.setDefaultMaxPerRoute(Integer.getInteger("client5.PoolingMaxConnectionSize", 5));
		LOGGER.info("defaultMaxPerRoute updated to:{}", connectionManager.getDefaultMaxPerRoute());
		final HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom().setConnectionManager(connectionManager);
		final Builder builder = IOReactorConfig.custom();
//		final IOReactorConfig ioReactor = IOReactorConfig.DEFAULT;
//		Builder builder = IOReactorConfig.copy(ioReactor);

//		int availableProcessors = Runtime.getRuntime().availableProcessors();
//		final Integer count = Integer.getInteger("client5.IOThreadCount", availableProcessors /2);
//		builder.setIoThreadCount(count);
//		LOGGER.info("IoThreadCount default:{}, actual :{}", Builder.getDefaultMaxIOThreadCount(), count);
//		builder.setSelectInterval(TimeValue.of(1, TimeUnit.MILLISECONDS));
//		builder.setSoReuseAddress(true);
		final IOReactorConfig ioReactorConfig = builder.setSoTimeout(Timeout.ofSeconds(5)).build();
		clientBuilder.setIOReactorConfig(ioReactorConfig);
		final CloseableHttpAsyncClient client = HttpAsyncClients.custom().setConnectionManager(connectionManager)
				.setIOReactorConfig(ioReactorConfig).build();

		client.start();

//        final HttpHost target = new HttpHost("https", "nghttp2.org");

//        warmUp(client, target);

		System.out.println("Connection pool stats: " + connectionManager.getTotalStats());

//        fireMultiple(client, target);

		fireParallel(client);

		// There still should be a single connection in the pool
		System.out.println("Connection pool stats: " + connectionManager.getTotalStats());

		System.out.println("Shutting down");
		client.close(CloseMode.GRACEFUL);
	}

	static class CountHolder {
		long value;

		public void incr(long value) {
			this.value += value;
		}
	}
	
	static  class CountTracker {
		private final Map<Thread, CountHolder> map = new ConcurrentHashMap<>();
		private final String name;

		public CountTracker(String name) {
			this.name = name;
		}

		public void add(long value) {
			map.computeIfAbsent(Thread.currentThread(), t -> new CountHolder()).incr(value);
		}

		@Override
		public String toString() {
			Set<Entry<Thread, CountHolder>> entrySet = map.entrySet();
			StringBuilder sb = new StringBuilder("\n").append(name).append('\n');
			long sum = 0;
			for (Entry<Thread, CountHolder> entry : entrySet) {
				final long value = entry.getValue().value;
				sum += value;
				sb/*.append(entry.getKey().getName()).append(':')*/
				.append(value).append('\n');
			}
			sb.append(sum).append('\n');
			return sb.toString();
		}

	}
	private static void fireParallel(CloseableHttpAsyncClient client) {

		
		final var sendCounter = new CountTracker("sent counter");
		final var successCounter = new CountTracker("successCounter");
		final var failureCounter = new CountTracker("failureCounter");

		Consumer<RESTClientFailedResponse> failureHandler = t -> {
			failureCounter.add(1L);
			LOGGER.error("", t.exception);
		};

		Consumer<RESTClientSuccessResponse> successHandler = t -> successCounter.add(1L);

		Thread stats = new Thread(() -> {
			Thread thread = Thread.currentThread();
			while (!thread.isInterrupted()) {
				LOGGER.info(" Stats-sent:{}", sendCounter);
				LOGGER.info(" Stats-success:{}", successCounter);
				LOGGER.info(" Stats-failure:{}", failureCounter);
				try {
//					sendCounter.reset();
//					successCounter.reset();
//					failureCounter.reset();
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					LOGGER.error("", e);
					Thread.currentThread().interrupt();
				}
			}
		});

		stats.start();

		List<Thread> threads = new ArrayList<>();

		for (int j = 0; j < 10; j++) {
			Thread t = new Thread(() -> {
				for (int i = 0; i < 10000; i++) {
					send(client, sendCounter, failureHandler, successHandler, i);
				}
			});

			t.start();
			threads.add(t);
		}

		threads.forEach(t -> {
			try {
				t.join();
			} catch (InterruptedException e) {
				LOGGER.error("", e);
				Thread.currentThread().interrupt();
			}
		});

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			LOGGER.error("", e);
			Thread.currentThread().interrupt();
		}
	}

	private static void send(CloseableHttpAsyncClient client, final CountTracker sendCounter,
			Consumer<RESTClientFailedResponse> failureHandler, Consumer<RESTClientSuccessResponse> successHandler,
			int i) {
		StringBuilder sb = new StringBuilder("Request:");
		for (int x = 0; x < 50; x++) {
			sb.append("some junk:");
		}
		sb.append(i);

		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			LOGGER.error("", e);
			Thread.currentThread().interrupt();
		}
		sendCounter.add(1L);

		final SimpleHttpRequest request = SimpleRequestBuilder.post(URI)
				.setBody(sb.toString().getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON)
				.build();
		client.execute(request, new FutureCallback<SimpleHttpResponse>() {

			@Override
			public void completed(final SimpleHttpResponse response) {
				successHandler.accept(new RESTClientSuccessResponse(request, response));
			}

			@Override
			public void failed(final Exception ex) {
				failureHandler.accept(new RESTClientFailedResponse(request, ex));
			}

			@Override
			public void cancelled() {
				LOGGER.info("{} cancelled", request);
			}
		});

//					rsAsyncClientConnectionConfig.post(URI, sb.toString().getBytes(StandardCharsets.UTF_8),
//							ContentType.APPLICATION_JSON, // APPLICATION_OCTET_STREAM,
//							failureHandler, successHandler, Collections.emptyMap());
	}

//	private static void fireMultiple(final CloseableHttpAsyncClient client, final HttpHost target)
//			throws InterruptedException {
//		// Execute multiple requests over the HTTP/2 connection from the pool
//		final String[] requestUris = { "/httpbin", "/httpbin/ip", "/httpbin/user-agent", "/httpbin/headers" };
//		final CountDownLatch countDownLatch = new CountDownLatch(requestUris.length);
//
//		for (final String requestUri : requestUris) {
//			final SimpleHttpRequest request = SimpleRequestBuilder.get().setHttpHost(target).setPath(requestUri)
//					.build();
//
//			System.out.println("Executing request " + request);
//			client.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(),
//					new FutureCallback<SimpleHttpResponse>() {
//
//						@Override
//						public void completed(final SimpleHttpResponse response) {
//							countDownLatch.countDown();
//							System.out.println(request + "->" + new StatusLine(response));
//							System.out.println(response.getBody());
//						}
//
//						@Override
//						public void failed(final Exception ex) {
//							countDownLatch.countDown();
//							System.out.println(request + "->" + ex);
//						}
//
//						@Override
//						public void cancelled() {
//							countDownLatch.countDown();
//							System.out.println(request + " cancelled");
//						}
//
//					});
//		}
//
//		countDownLatch.await();
//	}

//	private static void warmUp(final CloseableHttpAsyncClient client, final HttpHost target)
//			throws InterruptedException, ExecutionException {
//		final SimpleHttpRequest warmup = SimpleRequestBuilder.get().setHttpHost(target).setPath("/httpbin").build();
//
//		// Make sure there is an open HTTP/2 connection in the pool
//		System.out.println("Executing warm-up request " + warmup);
//		final Future<SimpleHttpResponse> future = client.execute(SimpleRequestProducer.create(warmup),
//				SimpleResponseConsumer.create(), new FutureCallback<SimpleHttpResponse>() {
//
//					@Override
//					public void completed(final SimpleHttpResponse response) {
//						System.out.println(warmup + "->" + new StatusLine(response));
//						System.out.println(response.getBody());
//					}
//
//					@Override
//					public void failed(final Exception ex) {
//						System.out.println(warmup + "->" + ex);
//					}
//
//					@Override
//					public void cancelled() {
//						System.out.println(warmup + " cancelled");
//					}
//
//				});
//		future.get();
//
//		Thread.sleep(1000);
//	}

}
