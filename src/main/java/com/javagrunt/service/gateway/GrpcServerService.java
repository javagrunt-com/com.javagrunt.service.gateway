package com.javagrunt.service.gateway;

import externalscaler.ExternalScalerGrpc.ExternalScalerImplBase;
import externalscaler.Externalscaler.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.scheduling.annotation.Async;

@GrpcService
public class GrpcServerService extends ExternalScalerImplBase {

	private final ScalerService scaler;

	GrpcServerService(ScalerService scaler) {
		this.scaler = scaler;
	}

	@Override
	public void isActive(ScaledObjectRef request, StreamObserver<IsActiveResponse> responseObserver) {
		responseObserver.onNext(IsActiveResponse.newBuilder().setResult(scaler.isActive(request.getName(), request.getNamespace())).build());
		responseObserver.onCompleted();
	}

	@Override
	@Async
	public void streamIsActive(ScaledObjectRef request, StreamObserver<IsActiveResponse> responseObserver) {
		boolean active = scaler.isActive(request.getName(), request.getNamespace());
		responseObserver.onNext(IsActiveResponse.newBuilder().setResult(active).build());
		while (true) {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				responseObserver.onError(e);
				Thread.currentThread().interrupt();
			}
			boolean update = scaler.isActive(request.getName(), request.getNamespace());
			if (update != active) {
				active = update;
				responseObserver.onNext(IsActiveResponse.newBuilder().setResult(active).build());
			}
		}
	}

	@Override
	public void getMetricSpec(ScaledObjectRef request, StreamObserver<GetMetricSpecResponse> responseObserver) {
		responseObserver.onNext(GetMetricSpecResponse.newBuilder()
			.addMetricSpecs(MetricSpec.newBuilder()
				.setMetricName("requests")
				.setTargetSize(Integer.parseInt(request.getScalerMetadataOrDefault("threshold", "3")))
				.build())
			.build());
		responseObserver.onCompleted();
	}

	@Override
	public void getMetrics(GetMetricsRequest request, StreamObserver<GetMetricsResponse> responseObserver) {
		responseObserver.onNext(GetMetricsResponse.newBuilder()
			.addMetricValues(MetricValue.newBuilder()
				.setMetricName("requests")
				.setMetricValue(scaler.getMetric(request.getScaledObjectRef().getName(), request.getScaledObjectRef().getNamespace()))
				.build())
			.build());
		responseObserver.onCompleted();
	}

}