/*
 * Copyright (C) 2026 VeloRay Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.raytolfas.veloray.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Additional configuration for VeloRay-only features.
 */
public final class VeloRayConfiguration {

  private static final Logger logger = LogManager.getLogger(VeloRayConfiguration.class);

  private final CommandBridge commandBridge;

  private VeloRayConfiguration(CommandBridge commandBridge) {
    this.commandBridge = commandBridge;
  }

  public CommandBridge getCommandBridge() {
    return commandBridge;
  }

  public boolean validate() {
    boolean valid = true;

    if (commandBridge.connectTimeout() < 0) {
      logger.error("'command-bridge.connect-timeout' must be 0 or greater.");
      valid = false;
    }
    if (commandBridge.readTimeout() < 0) {
      logger.error("'command-bridge.read-timeout' must be 0 or greater.");
      valid = false;
    }

    for (Map.Entry<String, RconServer> entry : commandBridge.servers().entrySet()) {
      final String serverName = entry.getKey();
      final RconServer server = entry.getValue();

      if (server.port() <= 0 || server.port() > 65535) {
        logger.error("'command-bridge.servers.{}.port' must be between 1 and 65535.", serverName);
        valid = false;
      }
      if (server.password().isBlank()) {
        logger.error("'command-bridge.servers.{}.password' must not be blank.", serverName);
        valid = false;
      }
    }

    return valid;
  }

  public static VeloRayConfiguration read(Path path) throws IOException {
    final URL defaultConfigLocation = VeloRayConfiguration.class.getClassLoader()
        .getResource("default-veloray.toml");
    if (defaultConfigLocation == null) {
      throw new RuntimeException("Default VeloRay configuration file does not exist.");
    }

    try (CommentedFileConfig config = CommentedFileConfig.builder(path)
        .defaultData(defaultConfigLocation)
        .autosave()
        .preserveInsertionOrder()
        .sync()
        .build()) {
      config.load();

      final CommentedConfig bridgeConfig = config.get("command-bridge");
      return new VeloRayConfiguration(CommandBridge.fromConfig(bridgeConfig));
    }
  }

  public record CommandBridge(
      boolean enabled,
      int connectTimeout,
      int readTimeout,
      Map<String, RconServer> servers
  ) {

    private static CommandBridge fromConfig(@Nullable CommentedConfig config) {
      if (config == null) {
        return new CommandBridge(false, 3000, 3000, ImmutableMap.of());
      }

      final boolean enabled = config.getOrElse("enabled", false);
      final int connectTimeout = config.getIntOrElse("connect-timeout", 3000);
      final int readTimeout = config.getIntOrElse("read-timeout", 3000);
      final CommentedConfig serversConfig = config.get("servers");
      final Map<String, RconServer> servers = new LinkedHashMap<>();

      if (serversConfig != null) {
        for (UnmodifiableConfig.Entry entry : serversConfig.entrySet()) {
          if (!(entry.getValue() instanceof CommentedConfig serverConfig)) {
            throw new IllegalArgumentException(
                "Command bridge entry " + entry.getKey() + " must be a table.");
          }

          servers.put(cleanServerName(entry.getKey()), RconServer.fromConfig(serverConfig));
        }
      }

      return new CommandBridge(enabled, connectTimeout, readTimeout, ImmutableMap.copyOf(servers));
    }

    private static String cleanServerName(String name) {
      return name.replace("\"", "");
    }
  }

  public record RconServer(@Nullable String host, int port, String password) {

    private static RconServer fromConfig(CommentedConfig config) {
      final String host = config.get("host");
      final int port = config.getIntOrElse("port", 25575);
      final String password = config.getOrElse("password", "");
      return new RconServer(host == null || host.isBlank() ? null : host, port, password);
    }
  }
}
