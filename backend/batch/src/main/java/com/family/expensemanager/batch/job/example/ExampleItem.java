package com.family.expensemanager.batch.job.example;

/**
 * TEMPLATE — the unit of work flowing reader → processor → writer. A record per job (or a Doma entity when the
 * item is a table row).
 *
 * @author boyquynhluu
 */
public record ExampleItem(long id, String name) {
}
