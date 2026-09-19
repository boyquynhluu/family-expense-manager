package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.report.ReportFormat;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.ImportResult;
import com.family.expensemanager.expense.dto.ReceiptFile;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import com.family.expensemanager.expense.service.TransactionImportService;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final TransactionImportService transactionImportService;

    @PostMapping
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        log.info("create - start");
        return ApiResponse.ok(transactionService.create(
                CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.email(), CurrentUser.displayName(),
                request));
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("list - start, page={}, size={}", page, size);
        TransactionReportFilter filter = new TransactionReportFilter(walletId, categoryId, type, fromDate, toDate);
        return ApiResponse.ok(transactionService.listByFamilyPaged(CurrentUser.familyId(), filter, page, size));
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

    @GetMapping("/trash")
    public ApiResponse<List<TransactionResponse>> trash() {
        log.info("trash - start");
        return ApiResponse.ok(transactionService.listDeleted(CurrentUser.familyId()));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        log.info("restore - start, id={}", id);
        transactionService.restore(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/receipt")
    public ApiResponse<TransactionResponse> uploadReceipt(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        log.info("uploadReceipt - start, id={}", id);
        return ApiResponse.ok(transactionService.uploadReceipt(CurrentUser.familyId(), id, file));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id) {
        log.info("getReceipt - start, id={}", id);
        ReceiptFile receipt = transactionService.getReceipt(CurrentUser.familyId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .body(receipt.content());
    }

    @DeleteMapping("/{id}/receipt")
    public ApiResponse<Void> deleteReceipt(@PathVariable Long id) {
        log.info("deleteReceipt - start, id={}", id);
        transactionService.deleteReceipt(CurrentUser.familyId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/import")
    public ApiResponse<ImportResult> importTransactions(@RequestParam("file") MultipartFile file) {
        log.info("importTransactions - start, filename={}", file.getOriginalFilename());
        return ApiResponse.ok(transactionImportService.importFile(
                CurrentUser.familyId(), CurrentUser.userId(), CurrentUser.email(), CurrentUser.displayName(), file));
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
