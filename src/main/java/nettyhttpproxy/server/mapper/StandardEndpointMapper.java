/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package nettyhttpproxy.server.mapper;

import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nettyhttpproxy.EndpointMapper;
import nettyhttpproxy.MapResult;
import nettyhttpproxy.server.config.ActionConfiguration;
import nettyhttpproxy.server.config.BackendConfiguration;
import nettyhttpproxy.server.config.BackendSelector;
import nettyhttpproxy.server.config.ConfigurationNotValidException;
import nettyhttpproxy.server.config.RouteConfiguration;
import nettyhttpproxy.server.config.RoutingKey;

/**
 * Standard Endpoint mapping
 */
public class StandardEndpointMapper extends EndpointMapper {

    private final Map<String, BackendConfiguration> backends = new HashMap<>();
    private final List<String> allbackendids = new ArrayList<>();
    private final List<RouteConfiguration> routes = new ArrayList<>();
    private final Map<String, ActionConfiguration> actions = new HashMap<>();
    private final BackendSelector backendSelector;

    public StandardEndpointMapper(BackendSelector backendSelector) {
        this.backendSelector = backendSelector;
    }

    private final class RandomBackendSelector implements BackendSelector {

        @Override
        public List<String> selectBackends(HttpRequest request, RoutingKey key) {
            ArrayList<String> result = new ArrayList<>(allbackendids);
            Collections.shuffle(result);
            return result;
        }

    }

    public StandardEndpointMapper() {
        this.backendSelector = new RandomBackendSelector();
    }

    public void addBackend(BackendConfiguration backend) throws ConfigurationNotValidException {
        if (backends.put(backend.getId(), backend) != null) {
            throw new ConfigurationNotValidException("backend " + backend.getId() + " is already configured");
        }
        allbackendids.add(backend.getId());
    }

    public void addAction(ActionConfiguration action) throws ConfigurationNotValidException {
        if (actions.put(action.getId(), action) != null) {
            throw new ConfigurationNotValidException("action " + action.getId() + " is already configured");
        }
    }

    public void addRoute(RouteConfiguration route) throws ConfigurationNotValidException {
        if (routes.stream().anyMatch(s -> s.getId().equalsIgnoreCase(route.getId()))) {
            throw new ConfigurationNotValidException("route " + route.getId() + " is already configured");
        }
        routes.add(route);
    }

    @Override
    public MapResult map(HttpRequest request) {
        for (RouteConfiguration route : routes) {
            RoutingKey matchResult = route.matches(request);
            if (matchResult != null) {
                ActionConfiguration action = actions.get(route.getAction());
                if (action == null) {
                    return MapResult.NOT_FOUND;
                }
                List<String> selectedBackends = backendSelector.selectBackends(request, matchResult);
                for (String backendId : selectedBackends) {
                    switch (action.getType()) {
                        case ActionConfiguration.TYPE_PROXY: {
                            BackendConfiguration backend = this.backends.get(backendId);
                            if (backend != null && isAvailable(backend)) {
                                return new MapResult(backend.getHost(), backend.getPort(), MapResult.Action.PROXY);
                            }
                            break;
                        }
                        case ActionConfiguration.TYPE_CACHE:
                            BackendConfiguration backend = this.backends.get(backendId);
                            if (backend != null && isAvailable(backend)) {
                                return new MapResult(backend.getHost(), backend.getPort(), MapResult.Action.CACHE);
                            }
                            break;
                        default:
                            return MapResult.NOT_FOUND;
                    }
                }
            }
        }
        return MapResult.NOT_FOUND;
    }

    private boolean isAvailable(BackendConfiguration backend) {
        return true;
    }

}