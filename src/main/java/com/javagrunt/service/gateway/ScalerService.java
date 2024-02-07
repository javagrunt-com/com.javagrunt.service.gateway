package com.javagrunt.service.gateway;

public interface ScalerService {

	boolean isActive(String name, String namespace);

	void setActive(String name, String namespace, boolean active);

	long getMetric(String name, String namespace);

}
