package com.javagrunt.service.gateway;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "scaler")
public class ScalerEndpoint {

	private final ScalerService scaler;

	ScalerEndpoint(ScalerService scaler) {
		this.scaler = scaler;
	}

	@ReadOperation
	public boolean status(String name) {
		return scaler.isActive(name);
	}

	@WriteOperation
	public void toggle(String name) {
		scaler.setActive(name, !scaler.isActive(name));
	}

}
