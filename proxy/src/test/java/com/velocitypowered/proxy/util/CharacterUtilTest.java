/*
 * Copyright (C) 2018-2021 Velocity Contributors
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

package com.velocitypowered.proxy.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
class CharacterUtilTest {

  private static final String CHARACTERS = "!\\\"#$%&'()*+,-./0123456789:;<=>?"
      + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_'abcdefghijklmnopqrstuvwxyzР’РЋР’В«Р’В»";
  private static final String NON_ASCII_CHARACTERS = "Р№Р‚СџРµС”В¦РЎР‰Р°В¤вЂ”РєР‡Р‚РІвЂ“Р‚";

  @Test
  void testCharacter() {
    assertTrue(CharacterUtil.isAllowedCharacter('a'));
    assertTrue(CharacterUtil.isAllowedCharacter(' '));

    assertFalse(CharacterUtil.isAllowedCharacter('\u00A7')); // Р’В§
    assertFalse(CharacterUtil.isAllowedCharacter('\u007F')); // DEL
    assertFalse(CharacterUtil.isAllowedCharacter((char) 0));
  }

  @Test
  public void testMessage() {
    assertFalse(CharacterUtil.containsIllegalCharacters(""));
    assertFalse(CharacterUtil.containsIllegalCharacters(" "));
    assertFalse(CharacterUtil.containsIllegalCharacters("Velocity"));
    assertFalse(CharacterUtil.containsIllegalCharacters(CHARACTERS));
    assertFalse(CharacterUtil.containsIllegalCharacters(NON_ASCII_CHARACTERS));

    assertTrue(CharacterUtil.containsIllegalCharacters("Р’В§cVelocity"));
    assertTrue(CharacterUtil.containsIllegalCharacters("Р’В§"));
  }
}
