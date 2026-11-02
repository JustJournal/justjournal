/*
 * Copyright (c) 2003-2026 Lucas Holt
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
package com.justjournal.services.activitypub;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * A fixed RSA 2048 key pair made with {@code openssl genpkey} for these tests only, so signatures
 * can be compared with ones openssl made. It signs nothing outside the tests.
 */
final class TestKeys {
  static final String PUBLIC_PEM =
      """
      -----BEGIN PUBLIC KEY-----
      MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtM/lBqGfiGL1a1xzagbj
      Scjdj+PJpPwoQTR1wl4zm84I6sddviBiWpvN9B5LJdautZ5jH4tQjmV40lzZQiD6
      48BNa1duRUD0xAl6mUM5qR6Y8yFZLGpwr0pkm1jfExd5HGC64rcZjp4h9cKHCjp8
      eSHJveKMY+aZq7J+8TCU7kE3PFdxkrZME7TQjM44CORm7jrjIX33LyRtENHUvfcb
      LhyM7/t8EBjn3SUJSl2M0o7MYYFlwLIy56bGpRJ1peVozDl2UO/bpp1TkN21VOmM
      Euyj3mrJEBctmkUh/8qAA8N/C0+6UjbDzf/3zYWkDM+BI3nVRdUlXohps4Ov6MAs
      hwIDAQAB
      -----END PUBLIC KEY-----
      """;

  static final String PRIVATE_PEM =
      """
      -----BEGIN PRIVATE KEY-----
      MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC0z+UGoZ+IYvVr
      XHNqBuNJyN2P48mk/ChBNHXCXjObzgjqx12+IGJam830Hksl1q61nmMfi1COZXjS
      XNlCIPrjwE1rV25FQPTECXqZQzmpHpjzIVksanCvSmSbWN8TF3kcYLritxmOniH1
      wocKOnx5Icm94oxj5pmrsn7xMJTuQTc8V3GStkwTtNCMzjgI5GbuOuMhffcvJG0Q
      0dS99xsuHIzv+3wQGOfdJQlKXYzSjsxhgWXAsjLnpsalEnWl5WjMOXZQ79umnVOQ
      3bVU6YwS7KPeaskQFy2aRSH/yoADw38LT7pSNsPN//fNhaQMz4EjedVF1SVeiGmz
      g6/owCyHAgMBAAECggEATQ7u+hkGjw8xrexMScsesdL3zjuTtAs9MrDjYQ9ZqlSw
      SzLlWkKd+CS/rQL+HuW0NrYGY6qxjcZkLC8i7oJ0uny54f30gnJQ/n1VsRzL5a4j
      HDdBtGK2nJY1c1l4VMAjvvrgmv/S1gvByGX7vFm5s7w5KQJ0t5zPMDQV0r/UMQeE
      8d7+8yO7yOfaGzI/gG5hz8bD6zUjXyJxYIUcl2J/CL2wDkWH2TsHtcaVTrUJRWUJ
      Tpd+M8b0p5PhYS9JUOVnjPkQySIz6PblDpExRE05+7n1G3KWZUmR3Z1ULrfhlQes
      OFFDcDhBdis34d3iOxpnMPQoEZKyVFlHETvJer7aRQKBgQDtrk5gEJr2pUBMFohn
      89JzTUegXpJ2xbokLZV0QXY4EFnaMQjS7reloLstVpOjUC6pBboo01gO09zHTqEv
      4dem6j//cmpkrzZu6pB4aFg/SnTkVdEcecIV7UQtHwVu+BEe3RpsT1rTurRrd3UX
      47ABFUb5UUVErtmL7zVyIxEGhQKBgQDCv4HgmD/Lkf5YW03iytDmsKQcAUAH287o
      w4ACRxXjfDumQhRWrcQcQDueMRglOCGc8sK1Hi0E7qiqxUV/YRFXcr6CPxqic3uu
      OX6XevgW8K2GOfwu10CxadYdINrqJc18O1/w18nFGAznxq4VxxwBY1k7Fm+IfDZu
      U6ErdYJymwKBgQDGmoHvD7ENK+RgL+vnQPu3UPnq7VthV7NEi7p4nEKWckNCaPrA
      lqxp/7aoGdHgAx4aKij1+DN6oFm5SOCzS/mRBehYIVj5rNbFM8dodXhX2nV2XqcP
      LkaZX9s6ANPfd/WGXqa3nk984GPl8LmtLpphLVBTAC/rRleN8GIub1LIqQKBgQCA
      wiFanw6+xx3EyUkrgzCu9WbrZSAyke+xEWDXIKymJQNDHzlXPbgFfaptIsR2BzCi
      FhcJrJPTSP+XOKqEV2chzCmM76/qbDP/st+xw4m3kRUJQ9kqFwMcyX7Y71oNqUS8
      uHzsPZ+Xghk2PLPojiZD/K2IVYMUWBucwgozM5Q1tQKBgGvbc740z5E2RJ5qO/5m
      itHxn0gPyAqnFL8IFSdO18xD1SVVPoznD/1npoWpmMSJlZSWshO36iY6PIO82/Jq
      lPKzvQS6UCU0Zp7N1txjBBkB04SIN1pfv+bCpdlCbHng3qL4E+xyi6qbbstnDMoB
      XvalqoN2MfKm0u2vhPMwSZR8
      -----END PRIVATE KEY-----
      """;

  private TestKeys() {}

  static PrivateKey privateKey() throws GeneralSecurityException {
    return Pem.privateKey(PRIVATE_PEM);
  }

  static PublicKey publicKey() throws GeneralSecurityException {
    return Pem.publicKey(PUBLIC_PEM);
  }

  /** Some other key, for a sender that has rotated. */
  static KeyPair another() throws GeneralSecurityException {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
