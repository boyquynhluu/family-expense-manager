package com.family.expensemanager.batch.job.example;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * TEMPLATE — receives a whole chunk at once, inside the chunk's transaction. Only logs here.
 *
 * <p>For a real job write in batches (JDBC batch update / Doma {@code @BatchInsert}) and make the write
 * idempotent — an upsert or a "WHERE not already processed" condition — because a restarted chunk is
 * written again.
 *
 * @author boyquynhluu
 */
@Component
@Slf4j(topic = "ExampleWriter")
public class ExampleWriter implements ItemWriter<ExampleItem> {

    @Override
    public void write(Chunk<? extends ExampleItem> chunk) {
        chunk.forEach(item -> log.info("write {}", item));
    }
}
