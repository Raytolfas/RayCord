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

package com.velocitypowered.proxy.rcon;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/**
 * Minimal RCON client used by the built-in RayCord command bridge.
 */
public final class RconClient {

  private static final int SERVERDATA_RESPONSE_VALUE = 0;
  private static final int SERVERDATA_EXECCOMMAND = 2;
  private static final int SERVERDATA_AUTH_RESPONSE = 2;
  private static final int SERVERDATA_AUTH = 3;

  private RconClient() {
  }

  /**
   * Executes a command on a backend server over RCON.
   *
   * @param host backend host
   * @param port backend RCON port
   * @param password backend RCON password
   * @param connectTimeout socket connect timeout in milliseconds
   * @param readTimeout socket read timeout in milliseconds
   * @param command command to execute
   * @return the RCON response payload
   * @throws IOException if the connection, authentication, or command execution fails
   */
  public static Result execute(
      String host,
      int port,
      String password,
      int connectTimeout,
      int readTimeout,
      String command
  ) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), connectTimeout);
      socket.setSoTimeout(readTimeout);

      try (DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
           DataOutputStream output = new DataOutputStream(
               new BufferedOutputStream(socket.getOutputStream()))) {
        authenticate(input, output, password);
        return executeCommand(input, output, command);
      }
    }
  }

  private static void authenticate(
      DataInputStream input,
      DataOutputStream output,
      String password
  ) throws IOException {
    writePacket(output, 1, SERVERDATA_AUTH, password);

    while (true) {
      Packet packet = readPacket(input);
      if (packet.type == SERVERDATA_AUTH_RESPONSE) {
        if (packet.requestId == -1) {
          throw new IOException("RCON authentication failed.");
        }
        return;
      }
    }
  }

  private static Result executeCommand(
      DataInputStream input,
      DataOutputStream output,
      String command
  ) throws IOException {
    writePacket(output, 2, SERVERDATA_EXECCOMMAND, command);

    StringBuilder response = new StringBuilder();
    boolean receivedPacket = false;
    while (true) {
      try {
        Packet packet = readPacket(input);
        if (packet.requestId != 2) {
          continue;
        }
        if (packet.type == SERVERDATA_RESPONSE_VALUE || packet.type == SERVERDATA_EXECCOMMAND) {
          receivedPacket = true;
          if (!packet.body.isBlank()) {
            if (!response.isEmpty()) {
              response.append(System.lineSeparator());
            }
            response.append(packet.body.trim());
          }
        }
      } catch (SocketTimeoutException ignored) {
        if (!receivedPacket) {
          throw new IOException("RCON command timed out before the server responded.");
        }
        break;
      } catch (EOFException ignored) {
        break;
      }
    }

    return new Result(response.toString().trim());
  }

  private static void writePacket(DataOutputStream output, int requestId, int type, String body)
      throws IOException {
    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
    int length = 4 + 4 + bodyBytes.length + 2;

    writeLittleEndianInt(output, length);
    writeLittleEndianInt(output, requestId);
    writeLittleEndianInt(output, type);
    output.write(bodyBytes);
    output.writeByte(0);
    output.writeByte(0);
    output.flush();
  }

  private static Packet readPacket(DataInputStream input) throws IOException {
    int length = readLittleEndianInt(input);
    if (length < 10) {
      throw new IOException("Invalid RCON packet length: " + length);
    }

    byte[] payload = input.readNBytes(length);
    if (payload.length != length) {
      throw new EOFException("Unexpected end of RCON stream.");
    }

    int requestId = readLittleEndianInt(payload, 0);
    int type = readLittleEndianInt(payload, 4);
    int bodyLength = Math.max(0, length - 10);
    String body = new String(Arrays.copyOfRange(payload, 8, 8 + bodyLength), StandardCharsets.UTF_8);
    return new Packet(requestId, type, body);
  }

  private static int readLittleEndianInt(DataInputStream input) throws IOException {
    int ch1 = input.readUnsignedByte();
    int ch2 = input.readUnsignedByte();
    int ch3 = input.readUnsignedByte();
    int ch4 = input.readUnsignedByte();
    return (ch4 << 24) | (ch3 << 16) | (ch2 << 8) | ch1;
  }

  private static int readLittleEndianInt(byte[] bytes, int offset) {
    return ((bytes[offset + 3] & 0xFF) << 24)
        | ((bytes[offset + 2] & 0xFF) << 16)
        | ((bytes[offset + 1] & 0xFF) << 8)
        | (bytes[offset] & 0xFF);
  }

  private static void writeLittleEndianInt(DataOutputStream output, int value) throws IOException {
    output.writeByte(value & 0xFF);
    output.writeByte((value >>> 8) & 0xFF);
    output.writeByte((value >>> 16) & 0xFF);
    output.writeByte((value >>> 24) & 0xFF);
  }

  /**
   * Result of a completed RCON command execution.
   *
   * @param response response body returned by the backend
   */
  public record Result(String response) {
  }

  private record Packet(int requestId, int type, String body) {
  }
}
