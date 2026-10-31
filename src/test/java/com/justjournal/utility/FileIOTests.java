/*
 * Copyright (c) 2003-2021 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal.utility;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FileIO wraps plain java.io file operations. */
class FileIOTests {

  @TempDir Path dir;

  private final FileIO fileIO = new FileIO();

  @Test
  void textFiles_roundTrip() throws IOException {
    final String path = dir.resolve("note.txt").toString();

    FileIO.writeTextFile(path, "héllo\nworld");
    assertEquals("héllo\nworld", FileIO.readTextFile(path));

    FileIO.writeTextFile(path, "replaced");
    assertEquals("replaced", FileIO.readTextFile(path));
    assertThrows(IOException.class, () -> FileIO.readTextFile(dir.resolve("missing.txt").toString()));
  }

  @Test
  void directories_canBeMadeAndRemoved() {
    final String path = dir.resolve("sub").toString();

    assertTrue(fileIO.makeDirectory(path));
    assertFalse(fileIO.makeDirectory(path));
    assertTrue(fileIO.deleteDirectory(path));
    assertFalse(fileIO.deleteDirectory(path));
    assertFalse(fileIO.deleteDirectory(dir.resolve("nope").toString()));
  }

  @Test
  void files_canBeTouchedMeasuredRenamedAndDeleted() throws IOException {
    final String path = dir.resolve("a.txt").toString();
    final String renamed = dir.resolve("b.txt").toString();

    assertTrue(fileIO.touchFile(path));
    assertFalse(fileIO.touchFile(path));
    assertFalse(fileIO.touchFile(dir.resolve("missing/dir/c.txt").toString()));
    assertEquals(0L, fileIO.fileLength(path));
    assertTrue(fileIO.fileLastModified(path) > 0L);

    FileIO.writeTextFile(path, "12345");
    assertEquals(5L, fileIO.fileLength(path));

    assertTrue(fileIO.renameFile(path, renamed));
    assertFalse(Files.exists(Path.of(path)));
    assertArrayEquals(new String[] {"b.txt"}, fileIO.listFiles(dir.toString()));

    assertTrue(fileIO.deleteFile(renamed));
    assertFalse(fileIO.deleteFile(renamed));
    assertFalse(fileIO.deleteFile(dir.toString()));
    assertEquals(0, fileIO.listFiles(dir.toString()).length);
    assertEquals(null, fileIO.listFiles(dir.resolve("nope").toString()));
  }
}
