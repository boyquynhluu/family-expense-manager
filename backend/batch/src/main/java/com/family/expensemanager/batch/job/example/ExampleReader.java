package com.family.expensemanager.batch.job.example;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

/**
 * TEMPLATE — supplies the items; returning {@code null} ends the step. Reads a fixed list here.
 *
 * <p>For a real table use a paging reader that walks by key, never OFFSET (slow on large tables, and rows
 * shift between pages while the job writes) — e.g. {@code JdbcPagingItemReaderBuilder} with
 * {@code .sortKeys(Map.of("id", Order.ASCENDING))}, or a Doma DAO selecting {@code WHERE id > :lastId ORDER BY id
 * LIMIT :pageSize}. Make it {@code @StepScope} when it needs job parameters
 * ({@code @Value("#{jobParameters['yearMonth']}")}).
 *
 * <p>{@code @StepScope}: a fresh reader per step execution — a singleton would stay exhausted after the first run.
 *
 * @author boyquynhluu
 */
@Component
@StepScope
public class ExampleReader extends ListItemReader<ExampleItem> {

    public ExampleReader() {
        super(List.of(new ExampleItem(1, "alpha"), new ExampleItem(2, "beta"), new ExampleItem(3, "gamma")));
    }
}
