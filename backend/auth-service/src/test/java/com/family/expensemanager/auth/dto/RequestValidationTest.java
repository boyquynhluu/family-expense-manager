package com.family.expensemanager.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.family.expensemanager.auth.domain.PhoneNumbers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void emails_areTrimmedAndLowerCased_soOneAccountHasOneLockoutKey() {
        assertThat(new LoginRequest("  Alice@Example.COM ", "pw").identifier()).isEqualTo("alice@example.com");
        assertThat(new RegisterRequest("F", "Bob@X.com", "password1", "Bob").email()).isEqualTo("bob@x.com");
        assertThat(new ForgotPasswordRequest(" A@B.com").email()).isEqualTo("a@b.com");
        assertThat(new ResendVerificationRequest("A@B.com ").email()).isEqualTo("a@b.com");
        assertThat(new InviteMemberRequest("A@B.com").email()).isEqualTo("a@b.com");
        assertThat(new ChangeEmailRequest("New@B.com", null, null).newEmail()).isEqualTo("new@b.com");
    }

    @Test
    void phoneNumbers_areNormalisedToPlus84_howeverTyped() {
        assertThat(new LoginRequest("0912 345 678", "pw").identifier()).isEqualTo("+84912345678");
        assertThat(new LoginRequest("84912345678", "pw").identifier()).isEqualTo("+84912345678");
        assertThat(new LoginRequest("+84 912.345-678", "pw").identifier()).isEqualTo("+84912345678");
        assertThat(new RegisterRequest("F", "a@b.com", "password1", "D", "0387654321").phone()).isEqualTo("+84387654321");
        assertThat(new UpdateProfileRequest("Tên", "Bố", "  ").phone()).isNull();
    }

    @Test
    void phone_isOptional_butMustBeAVietnameseMobileNumberWhenGiven() {
        assertThat(validator.validate(new RegisterRequest("Nhà Tài", "a@b.com", "password1", "Đức Tài", null))).isEmpty();
        assertThat(validator.validate(new RegisterRequest("Nhà Tài", "a@b.com", "password1", "Đức Tài", "0912345678"))).isEmpty();
        // Landline / too short / letters are all rejected with the phone message.
        for (String bad : new String[] {"0241234567", "091234567", "09123456789", "abc"}) {
            assertThat(validator.validate(new UpdateProfileRequest("Tên", "Bố", bad))).singleElement()
                    .satisfies(v -> assertThat(v.getMessage()).isEqualTo(PhoneNumbers.INVALID_MESSAGE));
        }
    }

    @Test
    void nullEmail_staysNull_andIsRejectedByNotBlank() {
        var request = new LoginRequest(null, "pw");
        assertThat(request.identifier()).isNull();
        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    void register_rejectsPasswordOverBcryptLimit_andOverlongNames() {
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "p".repeat(72), "D"))).isEmpty();
        // Over both limits at once (73 chars AND 73 bytes).
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "p".repeat(73), "D"))).hasSize(2);
        // Real-looking text over 100 characters (a single repeated letter like "FFF..." would also be junk → 2 errors).
        String over100 = "Gia đình Nguyễn ".repeat(7);
        assertThat(validator.validate(new RegisterRequest(over100, "a@b.com", "password1", "D"))).hasSize(1);
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "password1", over100))).hasSize(1);
    }

    @Test
    void names_rejectProfanityAndJunk_butAcceptNormalNames() {
        assertThat(validator.validate(new RegisterRequest("Nhà Tài", "a@b.com", "password1", "Đức Tài"))).isEmpty();
        assertThat(validator.validate(new RegisterRequest("test", "a@b.com", "password1", "Đức Tài")))
                .singleElement().satisfies(v -> assertThat(v.getMessage()).contains("không có ý nghĩa"));
        assertThat(validator.validate(new RegisterRequest("Nhà Tài", "a@b.com", "password1", "đồ l.ồ.n")))
                .singleElement().satisfies(v -> assertThat(v.getMessage()).isEqualTo("Nội dung có từ ngữ không phù hợp"));
    }

    @Test
    void newPasswords_areCappedAt72Utf8Bytes_notJust72Characters() {
        // "ệ" is 3 bytes in UTF-8: 24 of them = 72 bytes (ok), 25 = 75 bytes — only 25 characters, but BCrypt
        // would silently ignore everything past byte 72.
        String at72Bytes = "ệ".repeat(24);
        String over72Bytes = "ệ".repeat(25);
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", at72Bytes, "D"))).isEmpty();
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", over72Bytes, "D"))).hasSize(1);
        assertThat(validator.validate(new ResetPasswordRequest("tok", over72Bytes))).hasSize(1);
        assertThat(validator.validate(new ChangePasswordRequest("current", over72Bytes))).hasSize(1);
        assertThat(validator.validate(new AcceptInviteRequest("D", over72Bytes))).hasSize(1);
    }

    @Test
    void register_rejectsEmailLongerThanColumn() {
        String longEmail = "a".repeat(250) + "@b.com";
        assertThat(validator.validate(new RegisterRequest("F", longEmail, "password1", "D"))).isNotEmpty();
    }

    @Test
    void passwordChangeAndReset_enforceSameBounds() {
        assertThat(validator.validate(new ChangePasswordRequest("old", "p".repeat(73)))).hasSize(2); // over both @Size and @MaxUtf8Bytes
        assertThat(validator.validate(new ResetPasswordRequest("tok", "p".repeat(73)))).hasSize(2); // over both @Size and @MaxUtf8Bytes
        assertThat(validator.validate(new ResetPasswordRequest("tok", "short"))).hasSize(1);
    }

    @Test
    void profileAndInvite_optionalFieldsAreBoundedWhenPresent() {
        assertThat(validator.validate(new UpdateProfileRequest("Tên", "Bố"))).isEmpty();
        // Too long AND not one of the fixed choices.
        assertThat(validator.validate(new UpdateProfileRequest("Tên", "r".repeat(51)))).hasSize(2);
        assertThat(validator.validate(new UpdateProfileRequest("Tên", null))).isEmpty();
        assertThat(validator.validate(new UpdateProfileRequest("Tên", ""))).isEmpty();
        // Only the Profile page's fixed choices — no free text (profanity included) via the API.
        assertThat(validator.validate(new UpdateProfileRequest("Tên", "fuck"))).singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Quan hệ trong gia đình không hợp lệ"));
        assertThat(validator.validate(new AcceptInviteRequest(null, null))).isEmpty();
        assertThat(validator.validate(new AcceptInviteRequest("D", "p".repeat(73)))).hasSize(2); // over both @Size and @MaxUtf8Bytes
    }
}
