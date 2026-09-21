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

package com.velocitypowered.proxy.module;

import com.velocitypowered.proxy.config.RayCordConfiguration;
import com.velocitypowered.proxy.config.RayCordConfiguration.Preset;
import com.velocitypowered.proxy.config.ModuleConfigUtils;
import com.velocitypowered.proxy.module.antibot.AntiBotConfiguration;
import com.velocitypowered.proxy.module.antibot.AntiBotModule;
import com.velocitypowered.proxy.module.motd.MotdModule;
import com.velocitypowered.proxy.module.motd.MotdModuleConfiguration;
import com.velocitypowered.proxy.VelocityServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
/**
 * Manages built-in RayCord modules.
 */
public final class ModuleManager {

  private static final Logger logger = LogManager.getLogger(ModuleManager.class);
  private static final Path MODULES_DIRECTORY = Path.of("config", "modules");

  private final VelocityServer server;
  private final List<RayCordModule> activeModules = new ArrayList<>();

  public ModuleManager(VelocityServer server) {
    this.server = server;
  }

  /**
   * Reloads all built-in modules from the latest RayCord configuration.
   *
   * @param configuration the RayCord configuration
   * @throws IOException if a module configuration could not be loaded
   */
  public synchronized void reload(RayCordConfiguration configuration) throws IOException {
    shutdown();

    final RayCordConfiguration.Modules modules = configuration.getModules();
    if (!modules.enabled()) {
      logger.info("Built-in RayCord modules are disabled.");
      return;
    }

    if (modules.motd()) {
      final MotdModule motdModule = new MotdModule(
          server,
          MotdModuleConfiguration.read(
              MODULES_DIRECTORY.resolve("motd.toml"),
              modules.preset()
          )
      );
      motdModule.enable();
      activeModules.add(motdModule);
      logger.info("Enabled RayCord module '{}'.", motdModule.getId());
    }

    if (modules.antibot()) {
      final AntiBotModule antiBotModule = new AntiBotModule(
          server,
          AntiBotConfiguration.read(
              MODULES_DIRECTORY.resolve("antibot.toml"),
              modules.preset()
          )
      );
      antiBotModule.enable();
      activeModules.add(antiBotModule);
      logger.info("Enabled RayCord module '{}'.", antiBotModule.getId());
    }
  }

  /**
   * Applies a bundled preset to the module configuration files.
   *
   * @param preset the preset to apply
   * @throws IOException if the preset could not be copied
   */
  public synchronized void applyPreset(Preset preset) throws IOException {
    ModuleConfigUtils.overwriteWithBundledTemplate(
        MotdModuleConfiguration.class,
        MODULES_DIRECTORY.resolve("motd.toml"),
        preset.motdResource()
    );
    ModuleConfigUtils.overwriteWithBundledTemplate(
        AntiBotConfiguration.class,
        MODULES_DIRECTORY.resolve("antibot.toml"),
        preset.antibotResource()
    );
  }

  /**
   * Disables all active built-in modules.
   */
  public synchronized void shutdown() {
    for (int i = activeModules.size() - 1; i >= 0; i--) {
      final RayCordModule module = activeModules.get(i);
      try {
        module.disable();
      } catch (Exception e) {
        logger.error("Unable to disable RayCord module '{}'.", module.getId(), e);
      }
    }
    activeModules.clear();
  }

  /**
   * Returns the identifiers of currently active built-in modules.
   *
   * @return the active module identifiers
   */
  public synchronized List<String> getActiveModuleIds() {
    return Collections.unmodifiableList(
        activeModules.stream().map(RayCordModule::getId).collect(Collectors.toList())
    );
  }

  /**
   * Returns the known built-in module identifiers.
   *
   * @return the module identifiers
   */
  public List<String> getBuiltInModuleIds() {
    return List.of("motd", "antibot");
  }
}
