package org.comroid.oauth.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.comroid.api.StreamSupplier;
import org.comroid.auth.server.AuthServer;
import org.comroid.restless.REST;
import org.comroid.restless.server.RestEndpointException;
import org.comroid.restless.server.ServerEndpoint;
import org.comroid.uniform.Context;
import org.comroid.uniform.node.UniNode;
import org.intellij.lang.annotations.Language;

import java.util.regex.Pattern;

public enum OAuthEndpoint implements ServerEndpoint.This {
    AUTHORIZE("/authorize") {
        @Override
        public REST.Response executeGET(Context context, REST.Header.List headers, String[] urlParams, UniNode body) throws RestEndpointException {
            logger.debug("Received authorization request with body:\n{}", body.toSerializedString());
            return null;
        }
    };

    public static final StreamSupplier<ServerEndpoint> values = StreamSupplier.of(values());
    private static final Logger logger = LogManager.getLogger();
    private final String extension;
    private final String[] regExp;
    private final Pattern pattern;

    @Override
    public String getUrlBase() {
        return AuthServer.URL_BASE;
    }

    @Override
    public String getUrlExtension() {
        return "/oauth2" + extension;
    }

    @Override
    public String[] getRegExpGroups() {
        return regExp;
    }

    @Override
    public Pattern getPattern() {
        return pattern;
    }

    OAuthEndpoint(String extension, @Language("RegExp") String... regExp) {
        this.extension = extension;
        this.regExp = regExp;
        this.pattern = buildUrlPattern();
    }
}
