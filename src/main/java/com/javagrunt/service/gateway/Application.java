package com.javagrunt.service.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class Application {

	private final String hello = "http://hello-service";
	
	private final String root = "https://javagrunt.github.io";

	private final RequestCountingFilter scaler = new RequestCountingFilter();

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public ScalerService scaler() {
		return scaler; // new SimpleScalerService();
	}

	@Bean
	RouteLocator gateway(RouteLocatorBuilder rlb) {
		return rlb.routes()
				.route(r -> r.path("/hello-service/**").filters(f -> f.stripPrefix(1).filter(scaler)).uri(hello))
				.route(r -> r.path("/**").uri(root))
				.build();
	}
}

class RequestCountingFilter implements GatewayFilter, ScalerService {

	private final AtomicInteger count = new AtomicInteger();

	private volatile boolean active = true;

	private final GatewayFilter filter;

	RequestCountingFilter() {
		RetryGatewayFilterFactory.RetryConfig config = new RetryGatewayFilterFactory.RetryConfig();
		config.setBackoff(Duration.ofMillis(300), Duration.ofSeconds(1), 2, true);
		config.setRetries(10);
		filter = new RetryGatewayFilterFactory().apply(config);
	}

	@Override
	public long getMetric(String name) {
		return count.get();
	}

	@Override
	public boolean isActive(String name) {
		return active && count.get() > 0;
	}

	@Override
	public void setActive(String name, boolean active) {
		count.set(0);
		this.active = active;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		count.incrementAndGet();
		return this.filter.filter(exchange, chain).doOnError(e -> {
			if (count.get() > 0) {
				count.decrementAndGet();
			}
		}).then(Mono.fromRunnable(() -> {
			if (count.get() > 0) {
				count.decrementAndGet();
			}
		}));
	}

}
