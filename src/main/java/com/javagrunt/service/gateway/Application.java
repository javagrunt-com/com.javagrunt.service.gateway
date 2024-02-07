package com.javagrunt.service.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@SpringBootApplication
public class Application {
	
	private final RequestCountingFilter scaler = new RequestCountingFilter();

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public ScalerService scaler() {
		return scaler;
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb,
						 @Value("${hello-service.name}") String helloService,
						 @Value("${youtube-listener.name}") String youtubeListener,
						 @Value("${youtube-service.name}") String youtubeService,
						 @Value("${namespace}") String namespace,
						 @Value("${root.uri}") String root) {
		return rlb.routes()
				.route(r -> r.path("/hello-service/**")
						.filters(f -> f.stripPrefix(1)
							.filter(scaler))
						.metadata("name", helloService)
						.metadata("namespace", namespace)
						.uri(String.format("http://%s", helloService)))
				.route(r -> r.path("/youtube-service/**")
						.filters(f -> f.stripPrefix(1)
							.filter(scaler))
						.metadata("name", youtubeService)
						.metadata("namespace", namespace)
						.uri(String.format("http://%s", youtubeService)))
				.route(r -> r.path("/youtube-listener/**")
						.filters(f -> f.stripPrefix(1)
								.filter(scaler))
						.metadata("name", youtubeListener)
						.metadata("namespace", namespace)
						.uri(String.format("http://%s", youtubeListener)))
				.route(r -> r.path("/**").uri(root))
				.build();
	}
}

class RequestCountingFilter implements GatewayFilter, ScalerService {

	Logger logger = LoggerFactory.getLogger(RequestCountingFilter.class);

	private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
	
	private final GatewayFilter filter;

	RequestCountingFilter() {
		RetryGatewayFilterFactory.RetryConfig config = new RetryGatewayFilterFactory.RetryConfig();
		config.setBackoff(Duration.ofMillis(300), Duration.ofSeconds(1), 2, true);
		config.setRetries(10);
		filter = new RetryGatewayFilterFactory().apply(config);
	}

	@Override
	public long getMetric(String name, String namespace) {
		AtomicInteger count = getCount(name, namespace);
		logger.info("Returning count: " + count.get());
		return count.get();
	}

	@Override
	public boolean isActive(String name, String namespace) {
		AtomicInteger count = getCount(name, namespace);
		logger.info("Returning active: " + (count.get() > 0));
		return count.get() > 0;
	}

	@Override
	public void setActive(String name, String namespace, boolean active) {
		AtomicInteger count = getCount(name, namespace);
		if(count.get() > 0){
			count.set(0);
		}
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route r = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        assert r != null;
        String name = r.getMetadata().get("name").toString();
		String namespace = r.getMetadata().get("namespace").toString();
		AtomicInteger count = getCount(name, namespace);
		count.incrementAndGet();
		return this.filter.filter(exchange, chain).doOnError(e -> {
			if (count.get() > 0) {
				logger.info("Decrementing count");
				count.decrementAndGet();
			}
		}).then(Mono.fromRunnable(() -> {
			if (count.get() > 0) {
				logger.info("Decrementing count");
				count.decrementAndGet();
			}
		}));
	}
	
	private AtomicInteger getCount(String name, String namespace) {
		String key = String.format("%s:%s", name, namespace);
		if(!counts.containsKey(key)) {
			counts.put(key, new AtomicInteger(0));
		}
		return counts.get(key);
	}

}
