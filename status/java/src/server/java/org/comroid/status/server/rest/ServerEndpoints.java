package org.comroid.status.server.rest;

import org.comroid.restless.CommonHeaderNames;
import org.comroid.restless.REST;
import org.comroid.restless.endpoint.AccessibleEndpoint;
import org.comroid.restless.server.RestEndpointException;
import org.comroid.restless.server.ServerEndpoint;
import org.comroid.status.entity.Service;
import org.comroid.status.rest.Endpoint;
import org.comroid.status.server.StatusServer;
import org.comroid.status.server.auth.TokenCore;
import org.comroid.status.server.entity.LocalService;
import org.comroid.status.server.util.ResponseBuilder;
import org.comroid.uniform.Context;
import org.comroid.uniform.node.UniArrayNode;
import org.comroid.uniform.node.UniNode;

import java.io.FileNotFoundException;

import static org.comroid.restless.HTTPStatusCodes.*;

public enum ServerEndpoints implements ServerEndpoint {
    LIST_SERVICES(Endpoint.LIST_SERVICES, false) {
        @Override
        public REST.Response executeGET(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            final UniArrayNode services = StatusServer.CONTEXT.serialization.createArrayNode();

            StatusServer.instance
                    .getEntityCache()
                    .filterKey(name -> !name.equals("test-dummy"))
                    .flatMap(Service.class)
                    .forEach(service -> service.toObjectNode(services.addObject()));

            return new ResponseBuilder()
                    .setStatusCode(200)
                    .setBody(services)
                    .build();
        }
    },

    SPECIFIC_SERVICE(Endpoint.SPECIFIC_SERVICE, true) {
        @Override
        public REST.Response executeGET(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return StatusServer.instance.getServiceByName(urlParams[0])
                    .map(service -> service.toObjectNode(StatusServer.CONTEXT))
                    .map(node -> new ResponseBuilder()
                            .setStatusCode(200)
                            .setBody(node)
                            .build())
                    .orElseThrow(() -> new RestEndpointException(NOT_FOUND, "No service found with name " + urlParams[0]));
        }

        @Override
        public REST.Response executePUT(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            if (StatusServer.instance.getEntityCache().containsKey(urlParams[0]))
                throw new RestEndpointException(BAD_REQUEST, "Service " + urlParams[0] + " already exists!");

            checkAdminAuthorization(headers);

            final LocalService service = StatusServer.instance.createService(urlParams[0], body.asObjectNode());

            return new REST.Response(OK, service.toObjectNode(StatusServer.CONTEXT));
        }

        @Override
        public REST.Response executePATCH(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            checkAdminAuthorization(headers);

            final LocalService service = requireLocalService(urlParams[0]);
            service.updateFrom(body.asObjectNode());

            return new ResponseBuilder(body)
                    .setStatusCode(200)
                    .setBody(service)
                    .build();
        }

        @Override
        public REST.Response executeDELETE(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            checkAdminAuthorization(headers);

            final LocalService service = requireLocalService(urlParams[0]);

            if (StatusServer.instance.getEntityCache().remove(service))
                return new REST.Response(OK);
            throw new RestEndpointException(INTERNAL_SERVER_ERROR, "Could not remove service from cache");
        }
    },
    SERVICE_STATUS_ICON(Endpoint.SERVICE_STATUS_ICON, false) {
        @Override
        public REST.Response executeGET(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return StatusServer.instance.getServiceByName(urlParams[0])
                    .map(Service::getStatus)
                    .map(StatusIcon::valueOf)
                    .map(StatusIcon::getIconFile)
                    .map(icon -> {
                        try {
                            return new REST.Response(200, "image/png", icon);
                        } catch (FileNotFoundException e) {
                            throw new RestEndpointException(INTERNAL_SERVER_ERROR, e);
                        }
                    })
                    .orElseThrow(() -> new RestEndpointException(NOT_FOUND, "No service found with name " + urlParams[0]));
        }
    },
    UPDATE_SERVICE_STATUS(Endpoint.UPDATE_SERVICE_STATUS, false) {
        @Override
        public REST.Response executePOST(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            final LocalService service = requireLocalService(urlParams[0]);
            checkAuthorization(headers, service);

            final Service.Status newStatus = body.process("status")
                    .map(UniNode::asInt)
                    .map(Service.Status::valueOf)
                    .wrap()
                    .orElseThrow(() -> new RestEndpointException(BAD_REQUEST, "No new status defined"));

            service.setStatus(newStatus);

            return new ResponseBuilder(body)
                    .setStatusCode(200)
                    .setBody(service)
                    .build();
        }
    },

    POLL(Endpoint.POLL, false) {
        @Override
        public REST.Response executePOST(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            final LocalService service = requireLocalService(urlParams[0]);
            checkAuthorization(headers, service);

            final Service.Status newStatus = body.process("status")
                    .map(UniNode::asInt)
                    .map(Service.Status::valueOf)
                    .orElseThrow(() -> new RestEndpointException(BAD_REQUEST, "Missing status"));
            final int expected = body.get("expected").asInt(60);
            final int timeout = body.get("timeout").asInt(320);

            service.receivePoll(newStatus, expected, timeout);

            return new ResponseBuilder(body)
                    .setStatusCode(200)
                    .setBody(service)
                    .build();
        }

        @Override
        public REST.Response executeDELETE(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            final LocalService service = requireLocalService(urlParams[0]);
            checkAuthorization(headers, service);

            final Service.Status newStatus = body.process("status")
                    .map(UniNode::asInt)
                    .map(Service.Status::valueOf)
                    .orElseThrow(() -> new RestEndpointException(BAD_REQUEST, "Missing status"));

            service.discardPoll(newStatus);

            return new ResponseBuilder(body)
                    .setStatusCode(200)
                    .setBody(service)
                    .build();
        }
    };

    private final Endpoint underlying;

    private final boolean allowMemberAccess;

    @Override
    public AccessibleEndpoint getEndpointBase() {
        return underlying;
    }

    ServerEndpoints(Endpoint underlying, boolean allowMemberAccess) {
        this.underlying = underlying;
        this.allowMemberAccess = allowMemberAccess;
    }

    private static LocalService requireLocalService(String name) {
        return StatusServer.instance.getServiceByName(name)
                .flatMapOptional(it -> it.as(LocalService.class))
                .orElseThrow(() -> new RestEndpointException(NOT_FOUND, "No local service found with name " + name));
    }

    private static void checkAuthorization(REST.Header.List headers, LocalService service) {
        if (!headers.contains(CommonHeaderNames.AUTHORIZATION))
            throw new RestEndpointException(UNAUTHORIZED, "Unauthorized");

        final String token = headers.getFirst(CommonHeaderNames.AUTHORIZATION);

        if (service.getName().equals("test-dummy") && token.equals("null"))
            return;

        if (!TokenCore.isValid(token) || !TokenCore.extractName(token).equals(service.getName()))
            throw new RestEndpointException(UNAUTHORIZED, "Malicious Token used");
        if (!service.getToken().equals(token))
            throw new RestEndpointException(UNAUTHORIZED, "Unauthorized");
    }

    public static void checkAdminAuthorization(REST.Header.List headers) {
        if (!headers.contains(CommonHeaderNames.AUTHORIZATION))
            throw new RestEndpointException(UNAUTHORIZED, "Unauthorized");

        final String token = headers.getFirst(CommonHeaderNames.AUTHORIZATION);

        if (!TokenCore.isValid(token) || !TokenCore.extractName(token).equals(StatusServer.ADMIN_TOKEN_NAME))
            throw new RestEndpointException(UNAUTHORIZED, "Malicious Token used");
        if (!StatusServer.ADMIN_TOKEN.getContent().equals(token))
            throw new RestEndpointException(UNAUTHORIZED, "Unauthorized");
    }

    @Override
    public boolean allowMemberAccess() {
        return allowMemberAccess;
    }
}
