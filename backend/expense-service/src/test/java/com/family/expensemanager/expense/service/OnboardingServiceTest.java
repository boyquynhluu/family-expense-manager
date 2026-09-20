package com.family.expensemanager.expense.service;

import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Wallet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private CategoryDao categoryDao;
    @Mock
    private WalletDao walletDao;

    @InjectMocks
    private OnboardingService service;

    @Test
    void seedDefaults_createsCategoriesAndWallet_whenFamilyIsEmpty() {
        when(categoryDao.selectByFamilyId(1L)).thenReturn(List.of());
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of());

        var result = service.seedDefaults(1L);

        assertThat(result.categoriesCreated()).isEqualTo(11);
        assertThat(result.walletsCreated()).isEqualTo(1);

        ArgumentCaptor<Category> categories = ArgumentCaptor.forClass(Category.class);
        verify(categoryDao, times(11)).insert(categories.capture());
        assertThat(categories.getAllValues()).allSatisfy(c -> {
            assertThat(c.getFamilyId()).isEqualTo(1L);
            assertThat(c.getName()).isNotBlank();
            assertThat(c.getType()).isIn("EXPENSE", "INCOME");
        });
        assertThat(categories.getAllValues().stream().filter(c -> "EXPENSE".equals(c.getType()))).hasSize(8);
        assertThat(categories.getAllValues().stream().filter(c -> "INCOME".equals(c.getType()))).hasSize(3);

        ArgumentCaptor<Wallet> wallet = ArgumentCaptor.forClass(Wallet.class);
        verify(walletDao).insert(wallet.capture());
        assertThat(wallet.getValue().getFamilyId()).isEqualTo(1L);
        assertThat(wallet.getValue().getName()).isEqualTo("Tiền mặt");
        assertThat(wallet.getValue().getCurrency()).isEqualTo("VND");
        assertThat(wallet.getValue().getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void seedDefaults_skipsBoth_whenDataAlreadyExists() {
        when(categoryDao.selectByFamilyId(1L)).thenReturn(List.of(new Category()));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(new Wallet()));

        var result = service.seedDefaults(1L);

        assertThat(result.categoriesCreated()).isZero();
        assertThat(result.walletsCreated()).isZero();
        verify(categoryDao, never()).insert(any());
        verify(walletDao, never()).insert(any());
    }

    @Test
    void seedDefaults_seedsOnlyWallet_whenCategoriesAlreadyExist() {
        when(categoryDao.selectByFamilyId(1L)).thenReturn(List.of(new Category()));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of());

        var result = service.seedDefaults(1L);

        assertThat(result.categoriesCreated()).isZero();
        assertThat(result.walletsCreated()).isEqualTo(1);
        verify(categoryDao, never()).insert(any());
        verify(walletDao).insert(any(Wallet.class));
    }

    @Test
    void seedDefaults_seedsOnlyCategories_whenWalletAlreadyExists() {
        when(categoryDao.selectByFamilyId(1L)).thenReturn(List.of());
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(new Wallet()));

        var result = service.seedDefaults(1L);

        assertThat(result.categoriesCreated()).isEqualTo(11);
        assertThat(result.walletsCreated()).isZero();
        verify(walletDao, never()).insert(any());
    }

    @Test
    void mutatingMethods_requireOwnerRole() {
        for (String name : List.of("seedDefaults")) {
            var methods = Arrays.stream(OnboardingService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).toList();
            assertThat(methods).as(name).isNotEmpty();
            assertThat(methods).as(name).allSatisfy(m -> {
                PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('OWNER')");
            });
        }
    }
}
