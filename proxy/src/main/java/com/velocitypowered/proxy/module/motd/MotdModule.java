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

import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.module.AbstractRayCordModule;
import com.velocitypowered.proxy.VelocityServer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
/**
 * Overrides the proxy MOTD response with a configurable design.
 */
public final class MotdModule extends AbstractRayCordModule {

  private final MotdModuleConfiguration configuration;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

  public MotdModule(VelocityServer server, MotdModuleConfiguration configuration) {
    super(server);
    this.configuration = configuration;
  }

  @Override
  public String getId() {
    return "motd";
  }

  @Override
  public void enable() {
    if (!configuration.enabled()) {
      return;
    }
    registerListener(this);
  }

  /**
   * Rewrites the outgoing ping response with the configured MOTD presentation.
   *
   * @param event the ping event
   */
  @Subscribe
  public void onProxyPing(ProxyPingEvent event) {
    final ServerPing currentPing = event.getPing();
    final InboundConnection connection = event.getConnection();

    final Component description = miniMessage.deserialize(String.join("\n",
        configuration.lines().stream()
            .map(line -> replacePlaceholders(line, currentPing, connection))
            .toList()));

    final ServerPing.Players players = configuration.nullPlayers()
        ? null
        : new ServerPing.Players(
            shownOnline(currentPing),
            shownMax(currentPing),
            buildSamplePlayers(currentPing, connection)
        );

    event.setPing(new ServerPing(
        currentPing.getVersion(),
        players,
        description,
        currentPing.getFavicon().orElse(null),
        currentPing.getModinfo().orElse(null)
    ));
  }

  private int shownOnline(ServerPing currentPing) {
    if (configuration.online() >= 0) {
      return configuration.online();
    }
    return currentPing.getPlayers()
        .map(ServerPing.Players::getOnline)
        .orElse(server.getPlayerCount());
  }

  private int shownMax(ServerPing currentPing) {
    if (configuration.max() >= 0) {
      return configuration.max();
    }
    return currentPing.getPlayers()
        .map(ServerPing.Players::getMax)
        .orElse(server.getConfiguration().getShowMaxPlayers());
  }

  private List<ServerPing.SamplePlayer> buildSamplePlayers(ServerPing currentPing,
      InboundConnection connection) {
    final List<ServerPing.SamplePlayer> samplePlayers = new ArrayList<>();

    if (configuration.samplePlayers().isEmpty()) {
      currentPing.getPlayers()
          .map(ServerPing.Players::getSample)
          .ifPresent(samplePlayers::addAll);
      return samplePlayers;
    }

    for (String rawLine : configuration.samplePlayers()) {
      final String rendered = plainText.serialize(
          miniMessage.deserialize(replacePlaceholders(rawLine, currentPing, connection)));
      samplePlayers.add(new ServerPing.SamplePlayer(
          rendered,
          UUID.nameUUIDFromBytes(rendered.getBytes(StandardCharsets.UTF_8))
      ));
    }
    return samplePlayers;
  }

  private String replacePlaceholders(String text, ServerPing ping, InboundConnection connection) {
    final String username = connection instanceof Player player ? player.getUsername() : "";
    return text
        .replace("%online%", Integer.toString(server.getPlayerCount()))
        .replace("%max%", Integer.toString(server.getConfiguration().getShowMaxPlayers()))
        .replace("%shown_online%", Integer.toString(shownOnline(ping)))
        .replace("%shown_max%", Integer.toString(shownMax(ping)))
        .replace("%version%", ping.getVersion().getName())
        .replace("%protocol%", Integer.toString(ping.getVersion().getProtocol()))
        .replace("%player%", username)
        .replace("%host%", connection.getVirtualHost()
            .map(java.net.InetSocketAddress::getHostString)
            .orElse(""));
  }
}
