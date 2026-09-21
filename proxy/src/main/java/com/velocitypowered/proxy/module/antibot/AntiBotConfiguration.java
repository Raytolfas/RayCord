/*
 * Copyright (C) 2026 Velocity Contributors
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

package com.velocitypowered.proxy.module.antibot;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.proxy.config.ModuleConfigUtils;
import com.velocitypowered.proxy.config.RayCordConfiguration.Preset;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
/**
 * Configuration for the built-in anti-bot module.
 */
public record AntiBotConfiguration(
    boolean enabled,
    int maxConnectionsPerWindow,
    int connectionWindowSeconds,
    int banDurationSeconds,
    int verificationTimeoutSeconds,
    int cleanupIntervalSeconds,
    String blockedMessage,
    String verificationTimeoutMessage,
    boolean logActions
) {

  private static final Logger logger = LogManager.getLogger(AntiBotConfiguration.class);

  /**
   * Reads the anti-bot module configuration from disk.
   *
   * @param path the config path
   * @return the loaded configuration
   * @throws IOException if the file could not be read
   */
  public static AntiBotConfiguration read(Path path) throws IOException {
    return read(path, "modules/default-antibot.toml");
  }

  /**
   * Reads the anti-bot module configuration from disk using the specified preset template.
   *
   * @param path the config path
   * @param preset the bundled preset resource to seed missing files with
   * @return the loaded configuration
   * @throws IOException if the file could not be read
   */
  public static AntiBotConfiguration read(Path path, Preset preset) throws IOException {
    return read(path, preset.antibotResource());
  }

  private static AntiBotConfiguration read(Path path, String defaultResource)
      throws IOException {
    try (CommentedFileConfig config = ModuleConfigUtils.open(
        AntiBotConfiguration.class,
        path,
        defaultResource
    )) {
      config.load();

      final AntiBotConfiguration loaded = new AntiBotConfiguration(
          config.getOrElse("enabled", true),
          config.getIntOrElse("max-connections-per-window", 5),
          config.getIntOrElse("connection-window-seconds", 20),
          config.getIntOrElse("ban-duration-seconds", 600),
          config.getIntOrElse("verification-timeout-seconds", 15),
          config.getIntOrElse("cleanup-interval-seconds", 1),
          config.getOrElse("blocked-message",
              "<red>Too many connections from your IP. Try again in %seconds_left%s."),
          config.getOrElse("verification-timeout-message",
              "<red>Connection verification timed out."),
          config.getOrElse("log-actions", true)
      );

      if (!loaded.validate()) {
        throw new IOException("Invalid anti-bot module configuration at " + path);
      }
      return loaded;
    }
  }

  private boolean validate() {
    boolean valid = true;

    if (maxConnectionsPerWindow <= 0) {
      logger.error("'antibot.max-connections-per-window' must be greater than 0.");
      valid = false;
    }
    if (connectionWindowSeconds <= 0) {
      logger.error("'antibot.connection-window-seconds' must be greater than 0.");
      valid = false;
    }
    if (banDurationSeconds <= 0) {
      logger.error("'antibot.ban-duration-seconds' must be greater than 0.");
      valid = false;
    }
    if (verificationTimeoutSeconds <= 0) {
      logger.error("'antibot.verification-timeout-seconds' must be greater than 0.");
      valid = false;
    }
    if (cleanupIntervalSeconds <= 0) {
      logger.error("'antibot.cleanup-interval-seconds' must be greater than 0.");
      valid = false;
    }

    return valid;
  }
}
