package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.report.ReportFormat;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import com.family.expensemanager.expense.service.TransactionReportService;
import com.family.expensemanager.expense.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/transactions")
@RequiredArgsConstructor
@Slf4j(topic = "TransactionController")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionReportService transactionReportService;

    @PostMapping
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        log.info("create - start");
        return ApiResponse.ok(transactionService.create(CurrentUser.familyId(), CurrentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<TransactionResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(transactionService.listByFamily(CurrentUser.familyId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> get(@PathVariable Long id) {
        log.info("get - start, id={}", id);
        return ApiResponse.ok(transactionService.get(CurrentUser.familyId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(transactionService.update(CurrentUser.familyId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        transactionService.delete(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "CSV") ReportFormat format) {
        log.info("export - start, format={}", format);
        TransactionReportFilter filter = new TransactionReportFilter(walletId, categoryId, type, fromDate, toDate);
        byte[] content = transactionReportService.export(CurrentUser.familyId(), filter, format);

        boolean excel = format == ReportFormat.EXCEL;
        String filename = "giao-dich-" + LocalDate.now() + (excel ? ".xlsx" : ".csv");
        MediaType mediaType = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
