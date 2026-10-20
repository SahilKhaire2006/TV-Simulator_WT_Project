package com.simulator.websocket;

import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Explicit ServerApplicationConfig to ensure TVServerEndpoint is registered
 * by Tomcat's WsSci even if classpath scanning is skipped.
 */
public class TVWebSocketConfig implements ServerApplicationConfig {

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        return Collections.emptySet();
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        Set<Class<?>> set = new HashSet<>();
        if (scanned != null) {
            set.addAll(scanned);
        }
        set.add(TVServerEndpoint.class);
        return set;
    }
}
