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

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.module.AbstractRayCordModule;
import com.velocitypowered.proxy.VelocityServer;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
/**
 * A proxy-side anti-bot module with IP flood protection and post-login verification.
 */
public final class AntiBotModule extends AbstractRayCordModule {

  private static final Logger logger = LogManager.getLogger(AntiBotModule.class);

  private final AntiBotConfiguration configuration;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final Map<String, Long> bannedIps = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedDeque<Long>> connectionAttempts =
      new ConcurrentHashMap<>();
  private final Map<UUID, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();

  public AntiBotModule(VelocityServer server, AntiBotConfiguration configuration) {
    super(server);
    this.configuration = configuration;
  }

  @Override
  public String getId() {
    return "antibot";
  }

  @Override
  public void enable() {
    if (!configuration.enabled()) {
      return;
    }
    registerListener(this);
    scheduleRepeating(this::cleanup, configuration.cleanupIntervalSeconds(),
        configuration.cleanupIntervalSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void disable() {
    pendingVerifications.clear();
    connectionAttempts.clear();
    bannedIps.clear();
    super.disable();
  }

  /**
   * Blocks login bursts from a single IP and applies a temporary ban when the limit is exceeded.
   *
   * @param event the pre-login event
   */
  @Subscribe
  public void onPreLogin(PreLoginEvent event) {
    final String ip = ipKey(event.getConnection().getRemoteAddress());
    final long now = System.currentTimeMillis();
    final long bannedUntil = bannedIps.getOrDefault(ip, 0L);

    if (bannedUntil > now) {
      event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
          miniMessage.deserialize(configuration.blockedMessage()
              .replace("%seconds_left%", Long.toString(Math.max(1L, (bannedUntil - now) / 1000L)))
              .replace("%ip%", ip))
      ));
      return;
    }

    final ConcurrentLinkedDeque<Long> attempts = connectionAttempts.computeIfAbsent(ip,
        ignored -> new ConcurrentLinkedDeque<>());
    pruneOldAttempts(attempts, now);
    attempts.addLast(now);

    if (attempts.size() >= configuration.maxConnectionsPerWindow()) {
      final long expiresAt = now + TimeUnit.SECONDS.toMillis(configuration.banDurationSeconds());
      bannedIps.put(ip, expiresAt);
      connectionAttempts.remove(ip);
      event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
          miniMessage.deserialize(configuration.blockedMessage()
              .replace("%seconds_left%", Integer.toString(configuration.banDurationSeconds()))
              .replace("%ip%", ip))
      ));

      if (configuration.logActions()) {
        logger.warn("AntiBot temporarily banned IP {} for {} seconds after {} connections.",
            ip, configuration.banDurationSeconds(), configuration.maxConnectionsPerWindow());
      }
    }
  }

  /**
   * Starts post-login verification tracking for the connected player.
   *
   * @param event the post-login event
   */
  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    final Player player = event.getPlayer();
    pendingVerifications.put(player.getUniqueId(), new PendingVerification(
        player,
        ipKey(player.getRemoteAddress()),
        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(
            configuration.verificationTimeoutSeconds())
    ));
  }

  /**
   * Marks the player as verified once client settings arrive.
   *
   * @param event the player settings event
   */
  @Subscribe
  public void onPlayerSettings(PlayerSettingsChangedEvent event) {
    markVerified(event.getPlayer());
  }

  /**
   * Marks the player as verified once the client brand packet arrives.
   *
   * @param event the client brand event
   */
  @Subscribe
  public void onPlayerBrand(PlayerClientBrandEvent event) {
    markVerified(event.getPlayer());
  }

  /**
   * Clears verification state when the player disconnects.
   *
   * @param event the disconnect event
   */
  @Subscribe
  public void onDisconnect(DisconnectEvent event) {
    pendingVerifications.remove(event.getPlayer().getUniqueId());
  }

  private void cleanup() {
    final long now = System.currentTimeMillis();

    bannedIps.entrySet().removeIf(entry -> entry.getValue() <= now);

    connectionAttempts.entrySet().removeIf(entry -> {
      pruneOldAttempts(entry.getValue(), now);
      return entry.getValue().isEmpty();
    });

    pendingVerifications.entrySet().removeIf(entry -> {
      final PendingVerification pending = entry.getValue();
      if (pending.expiresAt() > now) {
        return false;
      }

      if (pending.player().isActive()) {
        pending.player().disconnect(
            miniMessage.deserialize(configuration.verificationTimeoutMessage()
                .replace("%ip%", pending.ip()))
        );
        if (configuration.logActions()) {
          logger.warn("AntiBot disconnected {} from {} after verification timeout.",
              pending.player().getUsername(), pending.ip());
        }
      }
      return true;
    });
  }

  private void markVerified(Player player) {
    pendingVerifications.remove(player.getUniqueId());
  }

  private void pruneOldAttempts(ConcurrentLinkedDeque<Long> attempts, long now) {
    final long cutoff = now - TimeUnit.SECONDS.toMillis(configuration.connectionWindowSeconds());
    Long head;
    while ((head = attempts.peekFirst()) != null && head < cutoff) {
      attempts.pollFirst();
    }
  }

  private String ipKey(InetSocketAddress address) {
    if (address.getAddress() != null) {
      return address.getAddress().getHostAddress();
    }
    return address.getHostString();
  }

  private record PendingVerification(Player player, String ip, long expiresAt) {
  }
}
