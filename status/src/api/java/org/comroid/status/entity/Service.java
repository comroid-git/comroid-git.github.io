package org.comroid.status.entity;

import org.comroid.api.IntEnum;
import org.comroid.api.Invocable;
import org.comroid.api.Polyfill;
import org.comroid.common.ref.WrappedFormattable;
import org.comroid.status.DependenyObject;
import org.comroid.status.StatusConnection;
import org.comroid.uniform.ValueType;
import org.comroid.uniform.node.UniObjectNode;
import org.comroid.varbind.annotation.Location;
import org.comroid.varbind.annotation.RootBind;
import org.comroid.varbind.bind.GroupBind;
import org.comroid.varbind.bind.VarBind;
import org.comroid.varbind.container.DataContainerBase;
import org.intellij.lang.annotations.Language;

import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

@Location(Service.Bind.class)
public interface Service extends Entity, WrappedFormattable {
    @Language("RegExp")
    String NAME_REGEX = "\\w[\\w\\d-]+";

    default String getDisplayName() {
        return requireNonNull(Bind.DisplayName);
    }

    default Status getStatus() {
        return requireNonNull(Bind.Status);
    }

    default Optional<URL> getURL() {
        return wrap(Bind.URL);
    }

    @Override
    default String getDefaultFormattedName() {
        return getDisplayName();
    }

    @Override
    default String getAlternateFormattedName() {
        return getName();
    }

    enum Status implements IntEnum {
        UNKNOWN(0),

        OFFLINE(1),
        CRASHED(2),
        MAINTENANCE(3),

        NOT_RESPONDING(4),

        ONLINE(5);

        private final int value;

        @Override
        public int getValue() {
            return value;
        }

        Status(int value) {
            this.value = value;
        }

        public static Status valueOf(int value) {
            return Arrays.stream(values())
                    .filter(it -> it.value == value)
                    .findAny()
                    .orElse(UNKNOWN);
        }

        @Override
        public String toString() {
            return name();
        }
    }

    interface Bind extends Entity.Bind {
        @RootBind
        GroupBind<Service> Root
                = Entity.Bind.Root.subGroup("service", Invocable.ofConstructor(Polyfill.<Class<Service>>uncheckedCast(Basic.class)));
        VarBind<Service, String, String, String> DisplayName
                = Root.createBind("display_name")
                .extractAs(ValueType.STRING)
                .asIdentities()
                .onceEach()
                .setRequired(true)
                .build();
        VarBind<Service, Integer, Service.Status, Service.Status> Status
                = Root.createBind("status")
                .extractAs(ValueType.INTEGER)
                .andRemap(Service.Status::valueOf)
                .onceEach()
                .setRequired(true)
                .build();
        VarBind<Service, String, URL, URL> URL
                = Root.createBind("url")
                .extractAs(ValueType.STRING)
                .andRemap(Polyfill::url)
                .onceEach()
                .setRequired(false)
                .build();
    }

    final class Basic extends DataContainerBase<Entity> implements Service {
        public Basic(UniObjectNode node) {
            super(node);
        }
    }
}
