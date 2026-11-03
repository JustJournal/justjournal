package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.config.AdminProperties;
import com.justjournal.core.MailSender;
import com.justjournal.core.Settings;
import com.justjournal.model.QueueMail;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.cache.AdminTwoFactorRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import reactor.core.publisher.Mono;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminLoginServiceTests {

  private static final String PASSWORD = "correct horse battery staple";

  private static String paddedHash;

  private static String unpaddedHash;

  @Mock private AdminTwoFactorRepository repository;

  @Mock private QueueMailRepository queueMailRepository;

  @Mock private ObjectProvider<MailSender> mailSenderProvider;

  @Mock private MailSender mailSender;

  @Mock private Settings settings;

  private AdminLoginService service;

  /** Hash the way the argon2 CLI does, with small costs so the tests stay quick. */
  private static String argon2id(
      final String password, final int memory, final int iterations, final boolean padded) {
    final byte[] salt = "sixteen byte slt".getBytes(StandardCharsets.US_ASCII);
    final Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(
        new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memory)
            .withIterations(iterations)
            .withParallelism(1)
            .build());
    final byte[] hash = new byte[32];
    generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
    final Base64.Encoder encoder =
        padded ? Base64.getEncoder() : Base64.getEncoder().withoutPadding();
    return String.format(
        "$argon2id$v=19$m=%d,t=%d,p=1$%s$%s",
        memory, iterations, encoder.encodeToString(salt), encoder.encodeToString(hash));
  }

  @BeforeAll
  static void hashes() {
    paddedHash = argon2id(PASSWORD, 1024, 2, true);
    unpaddedHash = argon2id(PASSWORD, 1024, 2, false);
  }

  private AdminLoginService service(final String username, final String hash) {
    return new AdminLoginService(
        new AdminProperties(username, hash),
        repository,
        queueMailRepository,
        mailSenderProvider,
        settings);
  }

  @BeforeEach
  void setUp() {
    service = service("admin", paddedHash);
    when(settings.getSiteAdminEmail()).thenReturn("admin@justjournal.com");
    when(settings.getMailFrom()).thenReturn("noreply@justjournal.com");
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    when(mailSender.sendNow(any(QueueMail.class))).thenReturn(true);
    when(queueMailRepository.saveAndFlush(any(QueueMail.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(repository.tryAcquireSend(eq("admin"), any(Duration.class))).thenReturn(Mono.just(true));
    when(repository.saveCode(eq("admin"), anyString(), any(Duration.class)))
        .thenReturn(Mono.just(true));
    when(repository.deleteCode("admin")).thenReturn(Mono.just(true));
    when(repository.releaseSend("admin")).thenReturn(Mono.just(true));
    when(repository.clearAttempts("admin")).thenReturn(Mono.just(true));
    when(repository.incrementAttempts(eq("admin"), any(Duration.class)))
        .thenReturn(Mono.just(1L));
    when(repository.getAttempts("admin")).thenReturn(Mono.empty());
    when(repository.getCode("admin")).thenReturn(Mono.empty());
  }

  private String mailedCode() {
    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).saveAndFlush(mail.capture());
    assertEquals("admin@justjournal.com", mail.getValue().getTo());
    assertEquals(AdminLoginService.MAIL_PURPOSE, mail.getValue().getPurpose());
    final Matcher m = Pattern.compile("\\b([0-9]{6})\\b").matcher(mail.getValue().getBody());
    assertTrue(m.find(), "mail should carry a six digit code");
    return m.group(1);
  }

  private void codeIs(final String code) {
    when(repository.getCode("admin")).thenReturn(Mono.just(PasswordResetService.hash(code)));
  }

  /* ---- password hash ---- */

  @Test
  void paddedAndUnpaddedHashesBothVerify() {
    assertNotEquals(paddedHash, unpaddedHash);
    assertTrue(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash));
    assertTrue(AdminLoginService.verifyArgon2id(PASSWORD, unpaddedHash));
  }

  @Test
  void wrongPasswordDoesNotVerify() {
    assertFalse(AdminLoginService.verifyArgon2id("wrong", paddedHash));
    assertFalse(AdminLoginService.verifyArgon2id("wrong", unpaddedHash));
    assertFalse(AdminLoginService.verifyArgon2id("", unpaddedHash));
  }

  @Test
  void costSettingsComeFromTheHashString() {
    final String hash = argon2id(PASSWORD, 2048, 1, false);
    assertTrue(AdminLoginService.verifyArgon2id(PASSWORD, hash));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, hash.replace("t=1", "t=2")));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, hash.replace("m=2048", "m=1024")));
  }

  @Test
  void hashWithTheUsernameInFrontIsNotTheAdminFormat() {
    final String memberStyle = argon2id("admin" + PASSWORD, 1024, 2, true);
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, memberStyle));
  }

  @Test
  void unreadableHashesNeverVerify() {
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, ""));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, null));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, "not a hash"));
    final String argon2i = paddedHash.replace("argon2id", "argon2i");
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, argon2i));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash.replace("v=19", "v=16")));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash.replace(",p=1", "")));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash.replace("p=1", "p=x")));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash.replace("m=1024", "m=4")));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash + "$extra"));
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, paddedHash.replace("$argon2id", "!!")));
    final String badSalt = paddedHash.replaceFirst("p=1\\$[^$]+", "p=1\\$\\*\\*\\*");
    assertFalse(AdminLoginService.verifyArgon2id(PASSWORD, badSalt));
  }

  @Test
  void credentialsNeedBothTheUsernameAndThePassword() {
    assertTrue(service.checkCredentials("admin", PASSWORD));
    assertTrue(service.checkCredentials(" admin ", PASSWORD));
    assertFalse(service.checkCredentials("Admin", PASSWORD));
    assertFalse(service.checkCredentials("someone", PASSWORD));
    assertFalse(service.checkCredentials("admin", "wrong"));
    assertFalse(service.checkCredentials(null, PASSWORD));
    assertFalse(service.checkCredentials("admin", null));
  }

  @Test
  void unpaddedConfiguredHashWorks() {
    assertTrue(service("admin", unpaddedHash).checkCredentials("admin", PASSWORD));
  }

  @Test
  void nothingPassesWhileTheAdminAreaIsOff() {
    assertFalse(service("", paddedHash).checkCredentials("", PASSWORD));
    assertFalse(service("admin", "").checkCredentials("admin", ""));
  }

  /* ---- mailed code ---- */

  @Test
  void codeIsMailedToTheSiteAdminAndStoredHashed() {
    assertEquals(AdminLoginService.SendResult.SENT, service.sendCode("192.0.2.4"));

    final String code = mailedCode();
    verify(repository)
        .saveCode(eq("admin"), eq(PasswordResetService.hash(code)), eq(AdminLoginService.CODE_TTL));
    verify(mailSender).sendNow(any(QueueMail.class));
  }

  @Test
  void noSiteAdminAddressMailsNothing() {
    when(settings.getSiteAdminEmail()).thenReturn(" ");

    assertEquals(AdminLoginService.SendResult.NO_ADDRESS, service.sendCode("192.0.2.4"));
    verify(repository, never()).saveCode(anyString(), anyString(), any(Duration.class));
    verify(queueMailRepository, never()).saveAndFlush(any());
  }

  @Test
  void resendIsThrottled() {
    when(repository.tryAcquireSend(eq("admin"), any(Duration.class))).thenReturn(Mono.just(false));

    assertEquals(AdminLoginService.SendResult.THROTTLED, service.sendCode("192.0.2.4"));
    verify(repository, never()).saveCode(anyString(), anyString(), any(Duration.class));
    verify(queueMailRepository, never()).saveAndFlush(any());
  }

  @Test
  void codeThatCannotBeSentLeavesNothingWaiting() {
    final QueueMail saved = new QueueMail();
    saved.setId(42);
    when(queueMailRepository.saveAndFlush(any(QueueMail.class))).thenReturn(saved);
    when(mailSender.sendNow(any(QueueMail.class))).thenReturn(false);

    assertEquals(AdminLoginService.SendResult.FAILED, service.sendCode("192.0.2.4"));
    verify(queueMailRepository).deleteById(42);
    verify(repository).deleteCode("admin");
    verify(repository).releaseSend("admin");
  }

  @Test
  void codeThatCannotBeQueuedLeavesNothingWaiting() {
    when(queueMailRepository.saveAndFlush(any(QueueMail.class)))
        .thenThrow(new InvalidDataAccessResourceUsageException("insert into queue_mail failed"));

    assertEquals(AdminLoginService.SendResult.FAILED, service.sendCode("192.0.2.4"));
    verify(repository).deleteCode("admin");
    verify(repository).releaseSend("admin");
  }

  @Test
  void rightCodeWorksOnce() {
    codeIs("123456");

    assertEquals(AdminLoginService.VerifyResult.OK, service.verifyCode(" 123 456 "));
    verify(repository).deleteCode("admin");
    verify(repository).clearAttempts("admin");

    when(repository.getCode("admin")).thenReturn(Mono.empty());
    assertEquals(AdminLoginService.VerifyResult.INVALID, service.verifyCode("123456"));
  }

  @Test
  void wrongCodeLeavesTheCodeInPlace() {
    codeIs("123456");

    assertEquals(AdminLoginService.VerifyResult.INVALID, service.verifyCode("654321"));
    assertEquals(AdminLoginService.VerifyResult.INVALID, service.verifyCode(null));
    assertEquals(AdminLoginService.VerifyResult.INVALID, service.verifyCode("12345"));
    verify(repository, never()).deleteCode(anyString());
    verify(repository, never()).clearAttempts(anyString());
  }

  @Test
  void fifthWrongCodeLocksAndThrowsTheCodeAway() {
    codeIs("123456");
    when(repository.incrementAttempts(eq("admin"), any(Duration.class)))
        .thenReturn(Mono.just((long) AdminLoginService.MAX_ATTEMPTS));

    assertEquals(AdminLoginService.VerifyResult.LOCKED, service.verifyCode("654321"));
    verify(repository).deleteCode("admin");
  }

  @Test
  void rightCodeAfterTheLimitIsRefused() {
    codeIs("123456");
    when(repository.incrementAttempts(eq("admin"), any(Duration.class)))
        .thenReturn(Mono.just((long) AdminLoginService.MAX_ATTEMPTS + 1));

    assertEquals(AdminLoginService.VerifyResult.LOCKED, service.verifyCode("123456"));
    verify(repository, never()).getCode(anyString());
    verify(repository, never()).clearAttempts(anyString());
  }

  @Test
  void lockedOnceTheAttemptsAreUsedUp() {
    assertFalse(service.isLocked());

    when(repository.getAttempts("admin")).thenReturn(Mono.just("4"));
    assertFalse(service.isLocked());

    when(repository.getAttempts("admin"))
        .thenReturn(Mono.just(String.valueOf(AdminLoginService.MAX_ATTEMPTS)));
    assertTrue(service.isLocked());
  }
}
