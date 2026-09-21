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

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import com.velocitypowered.proxy.VelocityServer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
/**
 * Base class for built-in modules that need listeners or scheduled tasks.
 */
public abstract class AbstractRayCordModule implements RayCordModule {

  protected final VelocityServer server;
  private final List<Object> listeners = new ArrayList<>();
  private final List<ScheduledTask> tasks = new ArrayList<>();

  protected AbstractRayCordModule(VelocityServer server) {
    this.server = server;
  }

  protected final void registerListener(Object listener) {
    server.getEventManager().register(VelocityVirtualPlugin.INSTANCE, listener);
    listeners.add(listener);
  }

  protected final ScheduledTask scheduleRepeating(Consumer<ScheduledTask> consumer, long delay,
      long repeat, TimeUnit unit) {
    final ScheduledTask task = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, consumer)
        .delay(delay, unit)
        .repeat(repeat, unit)
        .schedule();
    tasks.add(task);
    return task;
  }

  protected final ScheduledTask scheduleRepeating(Runnable runnable, long delay, long repeat,
      TimeUnit unit) {
    final ScheduledTask task = server.getScheduler()
        .buildTask(VelocityVirtualPlugin.INSTANCE, runnable)
        .delay(delay, unit)
        .repeat(repeat, unit)
        .schedule();
    tasks.add(task);
    return task;
  }

  protected final void cancelOwnedTasks() {
    for (ScheduledTask task : tasks) {
      task.cancel();
    }
    tasks.clear();
  }

  protected final void unregisterOwnedListeners() {
    for (Object listener : listeners) {
      server.getEventManager().unregisterListener(VelocityVirtualPlugin.INSTANCE, listener);
    }
    listeners.clear();
  }

  @Override
  public void disable() {
    cancelOwnedTasks();
    unregisterOwnedListeners();
  }
}
