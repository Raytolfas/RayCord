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

package com.velocitypowered.proxy.module.motd;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.proxy.config.RayCordConfiguration.Preset;
import com.velocitypowered.proxy.config.ModuleConfigUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
/**
 * Configuration for the built-in MOTD module.
 */
public record MotdModuleConfiguration(
    boolean enabled,
    List<String> lines,
    int online,
    int max,
    boolean nullPlayers,
    List<String> samplePlayers
) {

  private static final Logger logger = LogManager.getLogger(MotdModuleConfiguration.class);

  /**
   * Reads the MOTD module configuration from disk.
   *
   * @param path the config path
   * @return the loaded configuration
   * @throws IOException if the file could not be read
   */
  public static MotdModuleConfiguration read(Path path) throws IOException {
    return read(path, "modules/default-motd.toml");
  }

  /**
   * Reads the MOTD module configuration from disk using the specified preset template.
   *
   * @param path the config path
   * @param preset the bundled preset resource to seed missing files with
   * @return the loaded configuration
   * @throws IOException if the file could not be read
   */
  public static MotdModuleConfiguration read(Path path, Preset preset) throws IOException {
    return read(path, preset.motdResource());
  }

  private static MotdModuleConfiguration read(Path path, String defaultResource)
      throws IOException {
    try (CommentedFileConfig config = ModuleConfigUtils.open(
        MotdModuleConfiguration.class,
        path,
        defaultResource
    )) {
      config.load();

      final MotdModuleConfiguration loaded = new MotdModuleConfiguration(
          config.getOrElse("enabled", true),
          readStringList(config, "lines"),
          config.getIntOrElse("online", -1),
          config.getIntOrElse("max", -1),
          config.getOrElse("null-players", false),
          readStringList(config, "sample-players")
      );

      if (!loaded.validate()) {
        throw new IOException("Invalid MOTD module configuration at " + path);
      }
      return loaded;
    }
  }

  private boolean validate() {
    boolean valid = true;

    if (online < -1) {
      logger.error("'motd.online' must be -1 or greater.");
      valid = false;
    }
    if (max < -1) {
      logger.error("'motd.max' must be -1 or greater.");
      valid = false;
    }
    if (lines.isEmpty()) {
      logger.error("'motd.lines' must contain at least one line.");
      valid = false;
    }

    return valid;
  }

  @SuppressWarnings("unchecked")
  private static List<String> readStringList(CommentedFileConfig config, String path) {
    final Object raw = config.get(path);
    if (raw instanceof List<?> list) {
      final List<String> result = new ArrayList<>();
      for (Object entry : list) {
        if (entry != null) {
          result.add(String.valueOf(entry));
        }
      }
      return List.copyOf(result);
    }
    return List.of();
  }
}
