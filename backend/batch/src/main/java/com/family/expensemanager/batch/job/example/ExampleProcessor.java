package com.family.expensemanager.batch.job.example;

import java.util.Locale;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * TEMPLATE — transforms or validates one item; returning {@code null} filters it out (counted as "filtered",
 * not written). Keep it free of I/O so it is trivial to unit-test.
 *
 * @author boyquynhluu
 */
@Component
public class ExampleProcessor implements ItemProcessor<ExampleItem, ExampleItem> {

    @Override
    public ExampleItem process(ExampleItem item) {
        return new ExampleItem(item.id(), item.name().toUpperCase(Locale.ROOT));
    }
}
