package com.family.expensemanager.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(new LoginRequest("  Alice@Example.COM ", "pw").email()).isEqualTo("alice@example.com");
        assertThat(new RegisterRequest("F", "Bob@X.com", "password1", "Bob").email()).isEqualTo("bob@x.com");
        assertThat(new ForgotPasswordRequest(" A@B.com").email()).isEqualTo("a@b.com");
        assertThat(new ResendVerificationRequest("A@B.com ").email()).isEqualTo("a@b.com");
        assertThat(new InviteMemberRequest("A@B.com").email()).isEqualTo("a@b.com");
        assertThat(new ChangeEmailRequest("New@B.com", null, null).newEmail()).isEqualTo("new@b.com");
    }

    @Test
    void nullEmail_staysNull_andIsRejectedByNotBlank() {
        var request = new LoginRequest(null, "pw");
        assertThat(request.email()).isNull();
        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    void register_rejectsPasswordOverBcryptLimit_andOverlongNames() {
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "p".repeat(72), "D"))).isEmpty();
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "p".repeat(73), "D"))).hasSize(1);
        assertThat(validator.validate(new RegisterRequest("F".repeat(101), "a@b.com", "password1", "D"))).hasSize(1);
        assertThat(validator.validate(new RegisterRequest("F", "a@b.com", "password1", "D".repeat(101)))).hasSize(1);
    }

    @Test
    void register_rejectsEmailLongerThanColumn() {
        String longEmail = "a".repeat(250) + "@b.com";
        assertThat(validator.validate(new RegisterRequest("F", longEmail, "password1", "D"))).isNotEmpty();
    }

    @Test
    void passwordChangeAndReset_enforceSameBounds() {
        assertThat(validator.validate(new ChangePasswordRequest("old", "p".repeat(73)))).hasSize(1);
        assertThat(validator.validate(new ResetPasswordRequest("tok", "p".repeat(73)))).hasSize(1);
        assertThat(validator.validate(new ResetPasswordRequest("tok", "short"))).hasSize(1);
    }

    @Test
    void profileAndInvite_optionalFieldsAreBoundedWhenPresent() {
        assertThat(validator.validate(new UpdateProfileRequest("Tên", "Bố"))).isEmpty();
        assertThat(validator.validate(new UpdateProfileRequest("Tên", "r".repeat(51)))).hasSize(1);
        assertThat(validator.validate(new AcceptInviteRequest(null, null))).isEmpty();
        assertThat(validator.validate(new AcceptInviteRequest("D", "p".repeat(73)))).hasSize(1);
    }
}
