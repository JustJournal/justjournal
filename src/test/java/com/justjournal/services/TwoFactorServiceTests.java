/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2023 Lucas Holt
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
package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.MailSender;
import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.model.QueueMail;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.model.UserTwoFactor;
import com.justjournal.model.UserTwoFactorRecoveryCode;
import com.justjournal.model.UserTwoFactorTrustedDevice;
import com.justjournal.model.api.TotpEnrollment;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserTwoFactorRecoveryCodeRepository;
import com.justjournal.repository.UserTwoFactorRepository;
import com.justjournal.repository.UserTwoFactorTrustedDeviceRepository;
import com.justjournal.repository.cache.TwoFactorRepository;
import com.justjournal.utility.Totp;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import reactor.core.publisher.Mono;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorServiceTests {

  private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  @Mock private UserContactRepository userContactRepository;

  @Mock private UserTwoFactorRepository userTwoFactorRepository;

  @Mock private UserTwoFactorRecoveryCodeRepository recoveryCodeRepository;

  @Mock private UserTwoFactorTrustedDeviceRepository trustedDeviceRepository;

  @Mock private TwoFactorRepository twoFactorRepository;

  @Mock private QueueMailRepository queueMailRepository;

  @Mock private ObjectProvider<MailSender> mailSenderProvider;

  @Mock private MailSender mailSender;

  @Mock private Settings settings;

  @Mock private PlatformTransactionManager transactionManager;

  @InjectMocks private TwoFactorService twoFactorService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(7);
    user.setUsername("jjsite");
    final UserContact contact = new UserContact();
    contact.setEmail("owner@justjournal.com");
    when(userContactRepository.findByUser(user)).thenReturn(contact);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    when(settings.getMailFrom()).thenReturn("noreply@justjournal.com");
    when(settings.getSiteName()).thenReturn("Just Journal");
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    when(mailSender.sendNow(any(QueueMail.class))).thenReturn(true);
    when(queueMailRepository.saveAndFlush(any(QueueMail.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userTwoFactorRepository.saveAndFlush(any(UserTwoFactor.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(twoFactorRepository.tryAcquireEmail(eq(7), any(Duration.class))).thenReturn(Mono.just(true));
    when(twoFactorRepository.saveEmailCode(eq(7), anyString(), any(Duration.class)))
        .thenReturn(Mono.just(true));
    when(twoFactorRepository.deleteEmailCode(7)).thenReturn(Mono.just(true));
    when(twoFactorRepository.releaseEmail(7)).thenReturn(Mono.just(true));
    when(twoFactorRepository.clearAttempts(7)).thenReturn(Mono.just(true));
    when(twoFactorRepository.incrementAttempts(eq(7), any(Duration.class))).thenReturn(Mono.just(1L));
    when(twoFactorRepository.savePendingTotpSecret(eq(7), anyString(), any(Duration.class)))
        .thenReturn(Mono.just(true));
    when(twoFactorRepository.deletePendingTotpSecret(7)).thenReturn(Mono.just(true));
    when(twoFactorRepository.markTotpStepUsed(eq(7), anyLong(), any(Duration.class)))
        .thenReturn(Mono.just(true));
  }

  private String mailedCode() {
    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).saveAndFlush(mail.capture());
    assertEquals("owner@justjournal.com", mail.getValue().getTo());
    assertEquals("two_factor_code", mail.getValue().getPurpose());
    final Matcher m = Pattern.compile("\\b([0-9]{6})\\b").matcher(mail.getValue().getBody());
    assertTrue(m.find(), "mail should carry a six digit code");
    return m.group(1);
  }

  private void queueMailFails() {
    when(queueMailRepository.saveAndFlush(any(QueueMail.class)))
        .thenThrow(new InvalidDataAccessResourceUsageException("insert into queue_mail failed"));
  }

  private UserTwoFactor enabled(final TwoFactorMethod method) {
    final UserTwoFactor twoFactor = new UserTwoFactor();
    twoFactor.setUserId(7);
    twoFactor.setMethod(method);
    twoFactor.setTotpSecret(method == TwoFactorMethod.TOTP ? SECRET : null);
    twoFactor.setCreated(new Date());
    when(userTwoFactorRepository.existsByUserId(7)).thenReturn(true);
    when(userTwoFactorRepository.findByUserId(7)).thenReturn(Optional.of(twoFactor));
    return twoFactor;
  }

  /* ---- e-mail ---- */

  @Test
  void emailEnrollmentMailsACodeRightAway() {
    twoFactorService.startEmailEnrollment(user);

    final String code = mailedCode();
    verify(twoFactorRepository)
        .saveEmailCode(eq(7), eq(PasswordResetService.hash(code)), eq(TwoFactorService.EMAIL_CODE_TTL));
    verify(mailSender).sendNow(any(QueueMail.class));
  }

  @Test
  void emailEnrollmentIsThrottled() {
    when(twoFactorRepository.tryAcquireEmail(eq(7), any(Duration.class))).thenReturn(Mono.just(false));

    assertThrows(BadRequestException.class, () -> twoFactorService.startEmailEnrollment(user));
    verify(queueMailRepository, never()).saveAndFlush(any());
  }

  @Test
  void emailCodeThatCannotBeSentFailsAndCanBeAskedForAgain() {
    when(mailSender.sendNow(any(QueueMail.class))).thenReturn(false);

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> twoFactorService.startEmailEnrollment(user));

    assertEquals(TwoFactorService.CODE_NOT_MAILED, e.getMessage());
    // the scheduler must not deliver a code the user was told never went out
    verify(queueMailRepository).deleteById(anyInt());
    verify(twoFactorRepository).deleteEmailCode(7);
    verify(twoFactorRepository).releaseEmail(7);
  }

  @Test
  void emailCodeThatCannotBeQueuedFails() {
    queueMailFails();

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> twoFactorService.startEmailEnrollment(user));

    assertEquals(TwoFactorService.CODE_NOT_MAILED, e.getMessage());
    verify(mailSender, never()).sendNow(any());
    verify(twoFactorRepository).releaseEmail(7);
  }

  @Test
  void emailEnrollmentNeedsAnAddress() {
    when(userContactRepository.findByUser(user)).thenReturn(null);

    assertThrows(BadRequestException.class, () -> twoFactorService.startEmailEnrollment(user));
  }

  @Test
  void emailEnrollmentRefusedWhenAlreadyOn() {
    enabled(TwoFactorMethod.EMAIL);

    assertThrows(BadRequestException.class, () -> twoFactorService.startEmailEnrollment(user));
  }

  @Test
  void rightEmailCodeTurnsItOnAndMailsANotice() {
    when(twoFactorRepository.getEmailCode(7))
        .thenReturn(Mono.just(PasswordResetService.hash("123456")));

    assertTrue(twoFactorService.confirmEmailEnrollment(user, " 123 456 "));

    final ArgumentCaptor<UserTwoFactor> saved = ArgumentCaptor.forClass(UserTwoFactor.class);
    verify(userTwoFactorRepository).saveAndFlush(saved.capture());
    assertEquals(TwoFactorMethod.EMAIL, saved.getValue().getMethod());
    assertEquals(7, saved.getValue().getUserId());
    verify(twoFactorRepository).deleteEmailCode(7);

    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).saveAndFlush(mail.capture());
    assertEquals("two_factor_enabled", mail.getValue().getPurpose());
  }

  @Test
  void emailEnrollmentFailsWhenTheNoticeCannotBeQueued() {
    when(twoFactorRepository.getEmailCode(7))
        .thenReturn(Mono.just(PasswordResetService.hash("123456")));
    queueMailFails();

    final BadRequestException e =
        assertThrows(
            BadRequestException.class, () -> twoFactorService.confirmEmailEnrollment(user, "123456"));

    assertEquals(TwoFactorService.NOTICE_NOT_MAILED, e.getMessage());
  }

  @Test
  void wrongEmailCodeDoesNothing() {
    when(twoFactorRepository.getEmailCode(7))
        .thenReturn(Mono.just(PasswordResetService.hash("123456")));

    assertFalse(twoFactorService.confirmEmailEnrollment(user, "654321"));
    verify(userTwoFactorRepository, never()).saveAndFlush(any());
    verify(twoFactorRepository, never()).deleteEmailCode(anyInt());
  }

  @Test
  void expiredEmailCodeDoesNothing() {
    when(twoFactorRepository.getEmailCode(7)).thenReturn(Mono.empty());

    assertFalse(twoFactorService.confirmEmailEnrollment(user, "123456"));
  }

  /* ---- authenticator ---- */

  @Test
  void totpEnrollmentHoldsTheSecretInRedis() {
    final TotpEnrollment enrollment = twoFactorService.startTotpEnrollment(user);

    assertEquals(32, enrollment.getSecret().length());
    assertTrue(enrollment.getOtpauthUri().startsWith("otpauth://totp/Just%20Journal:jjsite?secret="));
    verify(twoFactorRepository)
        .savePendingTotpSecret(7, enrollment.getSecret(), TwoFactorService.TOTP_ENROLL_TTL);
    verify(userTwoFactorRepository, never()).saveAndFlush(any());
  }

  @Test
  void qrCodeIsDrawnForAPendingEnrollment() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.just(SECRET));

    final Optional<byte[]> png = twoFactorService.enrollmentQrPng(user);

    assertTrue(png.isPresent());
    // PNG signature
    assertEquals((byte) 0x89, png.get()[0]);
    assertEquals('P', png.get()[1]);
  }

  @Test
  void noQrCodeWithoutAPendingEnrollment() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.empty());

    assertTrue(twoFactorService.enrollmentQrPng(user).isEmpty());
  }

  @Test
  void rightAppCodeTurnsItOnAndHandsOutRecoveryCodes() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.just(SECRET));
    final String code = Totp.codeAt(SECRET, twoFactorService.nowSeconds() / Totp.STEP_SECONDS);

    final List<String> codes = twoFactorService.confirmTotpEnrollment(user, code);

    assertEquals(TwoFactorService.RECOVERY_CODE_COUNT, codes.size());
    assertTrue(codes.get(0).matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
    assertEquals(codes.size(), codes.stream().distinct().count());

    final ArgumentCaptor<UserTwoFactor> saved = ArgumentCaptor.forClass(UserTwoFactor.class);
    verify(userTwoFactorRepository).saveAndFlush(saved.capture());
    assertEquals(TwoFactorMethod.TOTP, saved.getValue().getMethod());
    assertEquals(SECRET, saved.getValue().getTotpSecret());
    verify(twoFactorRepository).deletePendingTotpSecret(7);
    verify(recoveryCodeRepository).deleteByUserId(7);
    verify(recoveryCodeRepository, times(TwoFactorService.RECOVERY_CODE_COUNT))
        .save(any(UserTwoFactorRecoveryCode.class));
  }

  @Test
  void appEnrollmentSurvivesAFailedNotice() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.just(SECRET));
    final String code = Totp.codeAt(SECRET, twoFactorService.nowSeconds() / Totp.STEP_SECONDS);
    queueMailFails();

    final List<String> codes = twoFactorService.confirmTotpEnrollment(user, code);

    assertEquals(TwoFactorService.RECOVERY_CODE_COUNT, codes.size());
    verify(userTwoFactorRepository).saveAndFlush(any(UserTwoFactor.class));
    // the notice has its own transaction, so its failure can't roll back the enrollment
    verify(transactionManager)
        .getTransaction(
            argThat(
                definition ->
                    definition.getPropagationBehavior()
                        == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
  }

  @Test
  void wrongAppCodeLeavesItOff() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.just(SECRET));

    assertTrue(twoFactorService.confirmTotpEnrollment(user, "000000").isEmpty());
    verify(userTwoFactorRepository, never()).saveAndFlush(any());
  }

  @Test
  void appEnrollmentTimesOut() {
    when(twoFactorRepository.getPendingTotpSecret(7)).thenReturn(Mono.empty());

    assertThrows(BadRequestException.class, () -> twoFactorService.confirmTotpEnrollment(user, "000000"));
  }

  /* ---- login ---- */

  @Test
  void loginCodeIsOnlyMailedForTheEmailMethod() {
    enabled(TwoFactorMethod.TOTP);

    assertFalse(twoFactorService.sendLoginCode(user));
    verify(queueMailRepository, never()).saveAndFlush(any());
  }

  @Test
  void loginCodeIsMailedForTheEmailMethod() {
    enabled(TwoFactorMethod.EMAIL);

    assertTrue(twoFactorService.sendLoginCode(user));
    mailedCode();
  }

  @Test
  void loginCodeThatCannotBeSentFails() {
    enabled(TwoFactorMethod.EMAIL);
    when(mailSender.sendNow(any(QueueMail.class))).thenReturn(false);

    assertThrows(BadRequestException.class, () -> twoFactorService.sendLoginCode(user));
    verify(twoFactorRepository).releaseEmail(7);
  }

  @Test
  void appCodeLogsIn() {
    final UserTwoFactor twoFactor = enabled(TwoFactorMethod.TOTP);
    final String code = Totp.codeAt(SECRET, twoFactorService.nowSeconds() / Totp.STEP_SECONDS);

    assertEquals(TwoFactorService.VerifyResult.OK, twoFactorService.verify(7, code));

    assertNotNull(twoFactor.getLastUsed());
    verify(twoFactorRepository).clearAttempts(7);
  }

  @Test
  void appCodeCannotBeReplayed() {
    enabled(TwoFactorMethod.TOTP);
    when(twoFactorRepository.markTotpStepUsed(eq(7), anyLong(), any(Duration.class)))
        .thenReturn(Mono.just(false));
    final String code = Totp.codeAt(SECRET, twoFactorService.nowSeconds() / Totp.STEP_SECONDS);

    assertEquals(TwoFactorService.VerifyResult.INVALID, twoFactorService.verify(7, code));
  }

  @Test
  void wrongAppCodeIsInvalid() {
    enabled(TwoFactorMethod.TOTP);

    assertEquals(TwoFactorService.VerifyResult.INVALID, twoFactorService.verify(7, "000000"));
    verify(twoFactorRepository, never()).clearAttempts(anyInt());
  }

  @Test
  void mailedCodeLogsIn() {
    enabled(TwoFactorMethod.EMAIL);
    when(twoFactorRepository.getEmailCode(7))
        .thenReturn(Mono.just(PasswordResetService.hash("123456")));

    assertEquals(TwoFactorService.VerifyResult.OK, twoFactorService.verify(7, "123456"));
    verify(twoFactorRepository).deleteEmailCode(7);
  }

  @Test
  void tooManyWrongCodesLocksTheLogin() {
    enabled(TwoFactorMethod.TOTP);
    when(twoFactorRepository.incrementAttempts(eq(7), any(Duration.class)))
        .thenReturn(Mono.just((long) TwoFactorService.MAX_ATTEMPTS + 1));
    final String code = Totp.codeAt(SECRET, twoFactorService.nowSeconds() / Totp.STEP_SECONDS);

    assertEquals(TwoFactorService.VerifyResult.LOCKED, twoFactorService.verify(7, code));
    verify(twoFactorRepository, never()).markTotpStepUsed(anyInt(), anyLong(), any());
  }

  @Test
  void recoveryCodeLogsInOnceWhateverTheMethod() {
    enabled(TwoFactorMethod.TOTP);
    final UserTwoFactorRecoveryCode row = new UserTwoFactorRecoveryCode();
    row.setUserId(7);
    row.setCodeHash(PasswordResetService.hash("ABCDE23456"));
    when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(7, row.getCodeHash()))
        .thenReturn(Optional.of(row));

    assertEquals(TwoFactorService.VerifyResult.OK, twoFactorService.verify(7, "abcde-23456"));

    assertNotNull(row.getUsedAt());
    verify(recoveryCodeRepository).saveAndFlush(row);
  }

  @Test
  void usedRecoveryCodeIsInvalid() {
    enabled(TwoFactorMethod.EMAIL);
    when(recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(eq(7), anyString()))
        .thenReturn(Optional.empty());

    assertEquals(TwoFactorService.VerifyResult.INVALID, twoFactorService.verify(7, "ABCDE-23456"));
  }

  @Test
  void verifyWithoutTwoFactorIsInvalid() {
    when(userTwoFactorRepository.findByUserId(7)).thenReturn(Optional.empty());

    assertEquals(TwoFactorService.VerifyResult.INVALID, twoFactorService.verify(7, "123456"));
  }

  /* ---- management ---- */

  @Test
  void disableForgetsEverythingAndMailsANotice() {
    enabled(TwoFactorMethod.TOTP);

    twoFactorService.disable(user);

    verify(userTwoFactorRepository).deleteByUserId(7);
    verify(recoveryCodeRepository).deleteByUserId(7);
    verify(trustedDeviceRepository).deleteByUserId(7);
    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).saveAndFlush(mail.capture());
    assertEquals("two_factor_disabled", mail.getValue().getPurpose());
  }

  @Test
  void disableForAppUsersSurvivesAFailedNotice() {
    enabled(TwoFactorMethod.TOTP);
    queueMailFails();

    twoFactorService.disable(user);

    verify(userTwoFactorRepository).deleteByUserId(7);
  }

  @Test
  void disableForEmailUsersFailsWhenTheNoticeCannotBeQueued() {
    enabled(TwoFactorMethod.EMAIL);
    queueMailFails();

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> twoFactorService.disable(user));

    assertEquals(TwoFactorService.NOTICE_NOT_MAILED, e.getMessage());
  }

  @Test
  void disableWhenOffIsAnError() {
    when(userTwoFactorRepository.existsByUserId(7)).thenReturn(false);

    assertThrows(BadRequestException.class, () -> twoFactorService.disable(user));
  }

  @Test
  void statusReportsWhatThePageShows() {
    enabled(TwoFactorMethod.TOTP);
    when(recoveryCodeRepository.countByUserIdAndUsedAtIsNull(7)).thenReturn(8L);
    when(trustedDeviceRepository.countByUserId(7)).thenReturn(2L);

    final var status = twoFactorService.status(user);

    assertTrue(status.isEnabled());
    assertEquals(TwoFactorMethod.TOTP, status.getMethod());
    assertEquals("owner@justjournal.com", status.getEmail());
    assertEquals(8L, status.getRecoveryCodesLeft());
    assertEquals(2L, status.getTrustedBrowsers());
  }

  /* ---- remembered browsers ---- */

  @Test
  void rememberedBrowserIsRecognisedByItsHash() {
    when(trustedDeviceRepository.saveAndFlush(any(UserTwoFactorTrustedDevice.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final String token = twoFactorService.rememberDevice(7);

    final ArgumentCaptor<UserTwoFactorTrustedDevice> saved =
        ArgumentCaptor.forClass(UserTwoFactorTrustedDevice.class);
    verify(trustedDeviceRepository).saveAndFlush(saved.capture());
    assertEquals(PasswordResetService.hash(token), saved.getValue().getTokenHash());
    assertTrue(saved.getValue().getExpires().after(new Date()));

    when(trustedDeviceRepository.findByTokenHash(saved.getValue().getTokenHash()))
        .thenReturn(Optional.of(saved.getValue()));
    assertTrue(twoFactorService.isTrustedDevice(7, token));
    assertFalse(twoFactorService.isTrustedDevice(8, token), "another user's cookie");
    assertFalse(twoFactorService.isTrustedDevice(7, null));
    assertFalse(twoFactorService.isTrustedDevice(7, "nope"));
  }

  @Test
  void expiredBrowserIsForgotten() {
    final UserTwoFactorTrustedDevice device = new UserTwoFactorTrustedDevice();
    device.setUserId(7);
    device.setTokenHash(PasswordResetService.hash("old"));
    device.setExpires(new Date(System.currentTimeMillis() - 1000));
    when(trustedDeviceRepository.findByTokenHash(device.getTokenHash()))
        .thenReturn(Optional.of(device));

    assertFalse(twoFactorService.isTrustedDevice(7, "old"));
    verify(trustedDeviceRepository).delete(device);
  }
}
