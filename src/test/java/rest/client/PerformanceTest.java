/**
 * 
 */
package rest.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rest.AsyncClientConnectionConfig;
import rest.AsyncClientConnectionConfig.RESTClientFailedResponse;
import rest.AsyncClientConnectionConfig.RESTClientSuccessResponse;

/**
 * @author LabB
 *
 */

public class PerformanceTest {

	private static final String HOST = "localhost";
	private static final int PORT = Integer.getInteger("port", 18093);
	private static final String URI = // "http://httpbin.org/post" ;
			"http://" + HOST + ":"
					+ PORT
					+ "/hello";
	private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTest.class);

//	@Rule
//    public WireMockRule wireMockRule = new WireMockRule(8093);

	private static ClientAndServer clientAndServer;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		clientAndServer = ClientAndServer.startClientAndServer(PORT);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterAll
	static void tearDownAfterClass() throws Exception {
		if(clientAndServer!= null) {
			clientAndServer.stop();
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	void setUp() throws Exception {

	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	public void testPerformanceRest2() {
		System.setProperty("rest.dispatcher.parallellism", "200");
		final var sendCounter = new LongAdder();
		final var successCounter = new LongAdder();
		final var failureCounter = new LongAdder();

		Consumer<RESTClientFailedResponse> failureHandler = t -> {
			failureCounter.add(1L);
			LOGGER.error("", t.exception);
		};

		Consumer<RESTClientSuccessResponse> successHandler = t -> {
			successCounter.add(1L);
		};

		// REST API stub
		if(clientAndServer!= null) {
			clientAndServer.when(HttpRequest.request().withMethod("POST").withPath("/hello"))
					.respond(HttpResponse.response().withStatusCode(200).withBody("Hello, World!"));
		}

		AsyncClientConnectionConfig rsAsyncClientConnectionConfig = new AsyncClientConnectionConfig(
				Timeout.ofSeconds(2 * 60), Timeout.ofSeconds(2 * 60), TimeValue.of(2, TimeUnit.HOURS),
				TimeValue.of(1, TimeUnit.MINUTES), 3, TimeValue.of(3, TimeUnit.MINUTES));

//		RSCustomAsyncClientConnectionConfig rsAsyncClientConnectionConfig = new RSCustomAsyncClientConnectionConfig(
//				Timeout.ofSeconds(2 * 60), Timeout.ofSeconds(2 * 60), TimeValue.of(2, TimeUnit.HOURS),
//				TimeValue.of(1, TimeUnit.MINUTES), 3, TimeValue.of(3, TimeUnit.MINUTES));

		Thread stats = new Thread(() -> {
			Thread thread = Thread.currentThread();
			while (!thread.isInterrupted()) {
				LOGGER.info(" Stats-sent:" + sendCounter.longValue());
				LOGGER.info(" Stats-success:" + successCounter.longValue());
				LOGGER.info(" Stats-failure:" + failureCounter.longValue());
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
					rsAsyncClientConnectionConfig.post(URI, sb.toString().getBytes(StandardCharsets.UTF_8),
							ContentType.APPLICATION_JSON, // APPLICATION_OCTET_STREAM,
							failureHandler, successHandler, Collections.emptyMap());
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

//	@Test
//	@Disabled
//	public void testPerformanceOldRest() {
//
//		Builder builder = new OkHttpClient.Builder();
//		builder.connectionPool(new ConnectionPool(10, 60, TimeUnit.SECONDS))
////		.protocols(Arrays.asList(Protocol.H2_PRIOR_KNOWLEDGE))
//		;
//
//		OkHttpClient client = new OkHttpClient(builder);
//		ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(client);
//		RestTemplate restTemplate = new RestTemplate(factory);
//		// One most likely would want to use a callback for operation result
//		final var sendCounter = new LongAdder();
//		final var successCounter = new LongAdder();
//		final var failureCounter = new LongAdder();
//
//		Thread stats = new Thread(() -> {
//			while (true) {
//				LOGGER.info("" + (new Date()) + " Stats-sent:" + sendCounter.longValue());
//				LOGGER.info("" + (new Date()) + " Stats-success:" + successCounter.longValue());
//				LOGGER.info("" + (new Date()) + " Stats-failure:" + failureCounter.longValue());
//				try {
//					Thread.sleep(5000);
//				} catch (InterruptedException e) {
//				}
//			}
//		});
//
//		stats.start();
//
//		List<Thread> threads = new ArrayList<>();
//
//		for (int j = 0; j < 10; j++) {
//			Thread t = new Thread(() -> {
//				for (int i = 0; i < 10000; i++) {
//					StringBuilder sb = new StringBuilder("Request:");
//					for (int x = 0; x < 50; x++) {
//						sb.append("some junk:");
//					}
//					sb.append(i);
//
//					try {
//						Thread.sleep(1);
//					} catch (InterruptedException e) {}
//
//					try {
//						sendCounter.add(1L);
//						String response = restTemplate.postForEntity(URI, sb.toString(), String.class).getBody();
//
//						if (response.equals(sb.toString())) {
//							successCounter.add(1L);
//						} else {
//							failureCounter.add(1L);
//						}
//					}
//					catch (Exception e) {
//						failureCounter.add(1L);
//						e.printStackTrace();
//					}
//				}
//			});
//
//			t.start();
//			threads.add(t);
//		}
//
//		threads.forEach(t -> {
//			try {
//				t.join();
//			} catch (InterruptedException e) {
//			}
//		});
//
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//		}
//	}

	static void shutdownAndAwaitTermination(ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					System.err.println("Pool did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

}
