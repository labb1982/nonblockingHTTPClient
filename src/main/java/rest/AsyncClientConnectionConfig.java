package rest;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorConfig.Builder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * https://hc.apache.org/httpcomponents-client-5.2.x/examples.html This example
 * demonstrates how to use connection configuration on a per-route or a per-host
 * basis.
 */
public class AsyncClientConnectionConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncClientConnectionConfig.class);

	private static final AsyncResponseConsumer<SimpleHttpResponse> DEFAULT_RESPONSE_CONSUMER = SimpleResponseConsumer
			.create();

	public static final int MAX_CONN_PER_ROUTE = Integer.parseInt(System.getProperty("max.conn.per.route", "500"));
	public static final int MAX_CONN_TOTAL = Integer.parseInt(System.getProperty("max.conn.total", "5000"));

	private final CloseableHttpAsyncClient closeableHttpAsyncClient;

// = Timeout.ofMinutes(2)

	public AsyncClientConnectionConfig(final Timeout socketTimeoutInSeconds, Timeout connectTimeoutInSeconds,
			TimeValue ttlInSeconds, TimeValue validationAfterInactivity, int maxRetries,
			final TimeValue retryInterval) {
		this(socketTimeoutInSeconds, connectTimeoutInSeconds, ttlInSeconds, validationAfterInactivity, maxRetries,
				MAX_CONN_PER_ROUTE, MAX_CONN_TOTAL, retryInterval);
	}

	public AsyncClientConnectionConfig(final Timeout socketTimeoutInSeconds, Timeout connectTimeoutInSeconds,
			TimeValue ttlInSeconds, TimeValue validationAfterInactivity, int maxRetries, int maxConnPerRoute,
			int maxConnTotal, final TimeValue retryInterval) {
		final Resolver<HttpRoute, ConnectionConfig> connectionConfigResolver = route -> {
			// Use different settings for all secure (TLS) connections
			final HttpHost targetHost = route.getTargetHost();
			LOGGER.trace("targetHost:{}", targetHost);
//			if (route.isSecure()) {
			return ConnectionConfig.custom().setConnectTimeout(connectTimeoutInSeconds)
					.setSocketTimeout(socketTimeoutInSeconds).setValidateAfterInactivity(validationAfterInactivity)
					.setTimeToLive(ttlInSeconds).build();
//			} else {
//				return ConnectionConfig.custom().setConnectTimeout(connectTimeoutInSeconds)
//						.setSocketTimeout(socketTimeoutInSeconds).setValidateAfterInactivity(validationAfterInactivity)
//						.setTimeToLive(ttlInSeconds).build();
//			}
		};
		final String tlsVersion = System.getProperty("tls.version", "TLSv1.2");
		System.setProperty("https.protocols", tlsVersion);
		final Resolver<HttpHost, TlsConfig> tlsConfigResolver = host -> {

			TLS[] values = TLS.values();
			for (TLS tlsValue : values) {
				if (tlsValue.id.equalsIgnoreCase(tlsVersion)) {
					return TlsConfig.custom().setSupportedProtocols(tlsValue).setHandshakeTimeout(Timeout.ofSeconds(10))
							.setVersionPolicy(HttpVersionPolicy.NEGOTIATE).build();
				}
			}
			throw new RuntimeException("Garbage TLS Version:" + tlsVersion);

		};

		// final RSX509TrustManager trustManager = new RSX509TrustManager();
		SSLContext sslContext;
		try {
			sslContext = SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build();
		} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
			throw new RuntimeException(e);
		}

		final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create().setSslContext(sslContext).build();
		final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
				.setTlsStrategy(tlsStrategy)//
				.setConnectionConfigResolver(connectionConfigResolver)//
				.setTlsConfigResolver(tlsConfigResolver)//
				.setMaxConnPerRoute(maxConnPerRoute)//
				.setMaxConnTotal(maxConnTotal).build();
		
		
		
//		 final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
//	                .setDefaultTlsConfig(TlsConfig.custom()
//	                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
//	                        .build())
//	                .setMessageMultiplexing(true)
//	                .build();
		 
		LOGGER.info("defaultMaxPerRoute:{}", cm.getDefaultMaxPerRoute());
		cm.setDefaultMaxPerRoute(Integer.getInteger("client5.PoolingMaxConnectionSize", 5));
		LOGGER.info("defaultMaxPerRoute updated to:{}", cm.getDefaultMaxPerRoute());
		final HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom().setConnectionManager(cm);
		final IOReactorConfig ioReactor = IOReactorConfig.DEFAULT;
		Builder builder = IOReactorConfig.copy(ioReactor);

		int availableProcessors = Runtime.getRuntime().availableProcessors();
		final Integer count = Integer.getInteger("client5.IOThreadCount", availableProcessors * 2);
		builder.setIoThreadCount(count);
		LOGGER.info("IoThreadCount default:{}, actual :{}", Builder.getDefaultMaxIOThreadCount(), count);
		builder.setSelectInterval(TimeValue.of(1, TimeUnit.MILLISECONDS));
//		builder.setSoReuseAddress(true);
		clientBuilder.setIOReactorConfig(builder.build());

		final String PROXY_HOST = System.getProperty("proxy.host");
		final String PROXY_PORT = System.getProperty("proxy.port");
		if (StringUtils.isNotEmpty(PROXY_HOST)) {
			final HttpHost proxy = StringUtils.isEmpty(PROXY_PORT) ? new HttpHost(PROXY_HOST)
					: new HttpHost(PROXY_HOST, Integer.parseInt(PROXY_PORT));
			clientBuilder.setProxy(proxy);
		}

		clientBuilder.setRetryStrategy(new DefaultHttpRequestRetryStrategy(maxRetries, retryInterval));
		closeableHttpAsyncClient = clientBuilder.build();

		closeableHttpAsyncClient.start();

	}

	private final Map<String, ThreadLocal<SimpleRequestBuilder>> map = new ConcurrentHashMap<>();

	public void post(String uri, byte[] content, ContentType contentType,
			Consumer<RESTClientFailedResponse> failureHandler,
			Consumer<RESTClientSuccessResponse> completionResponseHandler, Map<String, String> headers) {

//		ThreadLocal<SimpleRequestBuilder> local = map.computeIfAbsent(uri,
//				t -> ThreadLocal.withInitial(() -> SimpleRequestBuilder.post(uri)/* .setVersion(TLS.V_1_2.version) */));
		final SimpleRequestBuilder post = SimpleRequestBuilder.post(uri) ;// local.get();
		helper(content, contentType, failureHandler, completionResponseHandler, headers, post);

	}

	public void put(String uri, byte[] content, ContentType contentType,
			Consumer<RESTClientFailedResponse> failureHandler,
			Consumer<RESTClientSuccessResponse> completionResponseHandler, Map<String, String> headers) {

		final SimpleRequestBuilder put = SimpleRequestBuilder.put(uri);
		helper(content, contentType, failureHandler, completionResponseHandler, headers, put);

	}

	public void delete(String uri, byte[] content, ContentType contentType,
			Consumer<RESTClientFailedResponse> failureHandler,
			Consumer<RESTClientSuccessResponse> completionResponseHandler, Map<String, String> headers) {

		final SimpleRequestBuilder delete = SimpleRequestBuilder.delete(uri);
		helper(content, contentType, failureHandler, completionResponseHandler, headers, delete);

	}

	private void helper(byte[] content, ContentType contentType, Consumer<RESTClientFailedResponse> failureHandler,
			Consumer<RESTClientSuccessResponse> completionResponseHandler, Map<String, String> headers,
			final SimpleRequestBuilder delete) {
		final SimpleRequestBuilder reqBuilder = delete.setBody(content, contentType);
		headers.forEach(reqBuilder::setHeader);
		final SimpleHttpRequest request = reqBuilder.build();

		final FutureCallback<SimpleHttpResponse> callback = new FutureCallback<>() {

			@Override
			public void completed(final SimpleHttpResponse response) {
				completionResponseHandler.accept(new RESTClientSuccessResponse(request, response));
			}

			@Override
			public void failed(final Exception ex) {
				failureHandler.accept(new RESTClientFailedResponse(request, ex));
			}

			@Override
			public void cancelled() {
				LOGGER.info("{} cancelled", request);
			}

		};

//		final Future<SimpleHttpResponse> future = 
		final BasicRequestProducer create;
		// create= SimpleRequestProducer.create(request);
		create = new BasicRequestProducer(request, new BasicAsyncEntityProducer(content, contentType));

		closeableHttpAsyncClient.execute(create, DEFAULT_RESPONSE_CONSUMER, callback);
//		try {
//			SimpleHttpResponse simpleHttpResponse = future.get();
//			LOGGER.info("RSAsyncClientConnectionConfig.process():{}", simpleHttpResponse);
//		} catch (ExecutionException e) {
//			final Throwable cause = e.getCause();
//			LOGGER.error("Error:", cause);
//		} catch (InterruptedException e) {
//			LOGGER.error("InterruptedException:", e);
//			Thread.currentThread().interrupt();
//		}
	}

	public void close() {
		LOGGER.info("Shutting down");
		closeableHttpAsyncClient.close(CloseMode.GRACEFUL);
	}

	public static class RESTClientFailedResponse {

		private final SimpleHttpRequest request;
		public final Exception exception;

		public RESTClientFailedResponse(SimpleHttpRequest request, Exception exception) {
			this.request = request;
			this.exception = exception;
		}

		@Override
		public String toString() {
			return "ClientFailedResponse [request=" + request + "]";
		}

	}

	public static class RESTClientSuccessResponse {

		private SimpleBody body;
		private StatusLine statusLine;
		private final SimpleHttpRequest request;

		public RESTClientSuccessResponse(SimpleHttpRequest request, SimpleHttpResponse response) {
			statusLine = new StatusLine(response);
			body = response.getBody();
			this.request = request;
		}

		public SimpleBody getBody() {
			return body;
		}

		public void setBody(SimpleBody body) {
			this.body = body;
		}

		public StatusLine getStatusLine() {
			return statusLine;
		}

		public void setStatusLine(StatusLine statusLine) {
			this.statusLine = statusLine;
		}

		@Override
		public String toString() {
			return "ClientSuccessResponse [body=" + body.getContentType() + " : " + body.getBodyText() + ", statusLine="
					+ statusLine + ", request=" + request + "]";
		}

	}
}
