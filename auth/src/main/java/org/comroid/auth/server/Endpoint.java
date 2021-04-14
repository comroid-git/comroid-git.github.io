package org.comroid.auth.server;

import com.sun.net.httpserver.Headers;
import org.comroid.api.ContextualProvider;
import org.comroid.api.Polyfill;
import org.comroid.auth.user.UserAccount;
import org.comroid.auth.user.UserSession;
import org.comroid.mutatio.ref.Reference;
import org.comroid.restless.CommonHeaderNames;
import org.comroid.restless.REST;
import org.comroid.restless.server.RestEndpointException;
import org.comroid.restless.server.ServerEndpoint;
import org.comroid.uniform.node.UniNode;
import org.comroid.util.ReaderUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Pattern;

import static org.comroid.auth.user.UserAccount.EMAIL;
import static org.comroid.restless.HTTPStatusCodes.*;

public enum Endpoint implements ServerEndpoint.This {
    FAVICON("favicon.ico") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return new REST.Response(Polyfill.uri("https://cdn.comroid.org/favicon.ico"));
        }
    },
    WIDGET("widget") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return new REST.Response(OK);
        }
    },
    API("api") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            try {
                UserSession session = UserSession.findSession(headers);

                String accept = headers.getFirst(CommonHeaderNames.ACCEPTED_CONTENT_TYPE);
                if (accept != null && accept.equals("application/json"))
                    return new REST.Response(OK, session.getSessionData());

                String dataWrapper = String.format("let sessionData = JSON.parse('%s');", session.getSessionData().toSerializedString());
                Reader page = ReaderUtil.combine('\n', new StringReader(dataWrapper));

                return new REST.Response(OK, "application/javascript", page);
            } catch (RestEndpointException ignored) {
                String accept = headers.getFirst(CommonHeaderNames.ACCEPTED_CONTENT_TYPE);
                if (accept != null && accept.equals("application/json"))
                    return new REST.Response(UNAUTHORIZED);

                String dataWrapper = "let sessionData = undefined;";
                Reader page = ReaderUtil.combine('\n', new StringReader(dataWrapper));

                return new REST.Response(OK, "application/javascript", page);
            }
        }
    },
    ACCOUNT("account") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return new REST.Response(OK);
        }
    },
    MODIFY_ACCOUNT("account/%s", "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b") {
        @Override
        public REST.Response executePATCH(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            try {
                UserSession session = UserSession.findSession(headers);
                UserAccount account = session.getAccount();

                if (account.getUUID().toString().equalsIgnoreCase(urlParams[0]))
                    throw new RestEndpointException(UNAUTHORIZED, "Invalid Session Cookie");

                account.updateFrom(body.asObjectNode());
                if (body.has("password")) {
                    Reference<String> email = body.use(EMAIL).map(UniNode::asString);

                    if (!body.has("previous_password"))
                        throw new RestEndpointException(BAD_REQUEST, "Old Password missing");
                    if (!body.use("previous_password")
                            .map(UniNode::asString)
                            .combine(email, UserAccount::encrypt)
                            .test(account.login::contentEquals))
                        throw new RestEndpointException(UNAUTHORIZED, "Old Password wrong");

                    body.use("password")
                            .map(UniNode::asString)
                            .combine(email, UserAccount::encrypt)
                            .consume(hash -> account.put(UserAccount.LOGIN, hash));
                }

                return new REST.Response(OK, account);
            } catch (RestEndpointException ignored) {
                return new REST.Response(UNAUTHORIZED);
            }
        }
    },
    REGISTRATION("register") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            REST.Header.List response = new REST.Header.List();
            response.add(CommonHeaderNames.CACHE_CONTROL, "no-cache");
            return new REST.Response(OK, response);
        }

        @Override
        public REST.Response executePOST(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            try {
                String email = body.use(EMAIL)
                        .map(UniNode::asString)
                        .requireNonNull("No Email provided");
                String password = body.use("password")
                        .map(UniNode::asString)
                        .requireNonNull("No Password provided");

                UserAccount account = AuthServer.instance.getUserManager().createAccount(email, password);

                return new REST.Response(OK, account);
            } catch (Throwable t) {
                throw new RestEndpointException(INTERNAL_SERVER_ERROR, "Could not create user account", t);
            }
        }
    },
    LOGIN("api/login") {
        @Override
        public REST.Response executePOST(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            try {
                String email = body.use(EMAIL)
                        .map(UniNode::asString)
                        .requireNonNull("No Email provided");
                String password = body.use("password")
                        .map(UniNode::asString)
                        .requireNonNull("No Password provided");

                UserSession session = AuthServer.instance.getUserManager().loginUser(email, password);

                REST.Header.List resp = new REST.Header.List();
                resp.add("Set-Cookie", session.getCookie());
                return forwardToWidgetOr(headers, resp, "account");
            } catch (Throwable t) {
                throw new RestEndpointException(INTERNAL_SERVER_ERROR, "Could not log in", t);
            }
        }

        @Override
        public REST.Response executeDELETE(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            return new REST.Response(Polyfill.uri("logout"));
        }
    },
    LOGOUT("logout") {
        @Override
        public REST.Response executeGET(ContextualProvider context, Headers headers, String[] urlParams, UniNode body) throws RestEndpointException {
            UserSession session = UserSession.findSession(headers);
            AuthServer.instance.getUserManager().closeSession(session);
            REST.Header.List response = new REST.Header.List();
            response.add(CommonHeaderNames.CACHE_CONTROL, "no-cache");
            response.add("Set-Cookie", UserSession.NULL_COOKIE);
            return forwardToWidgetOr(headers, response, "account");
        }
    };

    private final String extension;
    private final String[] regex;
    private final Pattern pattern;

    @Override
    public String getUrlBase() {
        return AuthServer.URL_BASE;
    }

    @Override
    public String getUrlExtension() {
        return extension;
    }

    @Override
    public String[] getRegExpGroups() {
        return regex;
    }

    @Override
    public Pattern getPattern() {
        return pattern;
    }

    Endpoint(String extension, @Language("RegExp") String... regex) {
        this.extension = extension;
        this.regex = regex;
        this.pattern = buildUrlPattern();
    }

    @NotNull
    private static REST.Response forwardToWidgetOr(Headers headers, REST.Header.List response, String other) {
        String referrer = headers.getFirst(CommonHeaderNames.REFERER);
        referrer = referrer == null ? "" : referrer.substring(referrer.lastIndexOf('/') + 1);
        boolean isWidget = referrer.equals("widget");
        response.add(CommonHeaderNames.REDIRECT_TARGET, isWidget ? "widget" : other);
        return new REST.Response(MOVED_PERMANENTLY, response);
    }
}
