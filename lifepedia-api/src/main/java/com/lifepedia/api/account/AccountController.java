package com.lifepedia.api.account;

import com.lifepedia.api.account.AccountDtos.AccountData;
import com.lifepedia.api.account.AccountDtos.DeleteRequest;
import com.lifepedia.api.account.AccountDtos.DeletionPreview;
import com.lifepedia.api.account.AccountDtos.ImportRequest;
import com.lifepedia.api.account.AccountDtos.ImportResult;
import com.lifepedia.api.auth.SessionCookies;
import com.lifepedia.api.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conta do usuário: exportar, importar e excluir. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountExportService exports;
    private final AccountImportService imports;
    private final AccountDeletionService deletion;
    private final SessionCookies cookies;

    public AccountController(AccountExportService exports, AccountImportService imports, AccountDeletionService deletion,
                             SessionCookies cookies) {
        this.exports = exports;
        this.imports = imports;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    /** Arquivo JSON importável ({@code lifepedia/account@1}). */
    @GetMapping("/export")
    public ResponseEntity<AccountData> export(@AuthenticationPrincipal Jwt jwt) {
        return attachment(AccountExportService.fileName("json"), MediaType.APPLICATION_JSON)
                .body(exports.export(CurrentUser.id(jwt)));
    }

    /** Mesmos dados em planilha (só leitura). */
    @GetMapping("/export/spreadsheet")
    public ResponseEntity<byte[]> spreadsheet(@AuthenticationPrincipal Jwt jwt) {
        return attachment(AccountExportService.fileName("xlsx"), XLSX).body(exports.spreadsheet(CurrentUser.id(jwt)));
    }

    /** Importa no financeiro pessoal; {@code dryRun} devolve só a prévia. */
    @PostMapping("/import")
    public ImportResult importData(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        return imports.importData(CurrentUser.id(jwt), request);
    }

    @GetMapping("/deletion-preview")
    public DeletionPreview deletionPreview(@AuthenticationPrincipal Jwt jwt) {
        return deletion.preview(CurrentUser.id(jwt));
    }

    /** Exclui a conta (senha + "EXCLUIR") e apaga o cookie de sessão. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteRequest request) {
        deletion.delete(CurrentUser.id(jwt), request);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.expired().toString()).build();
    }

    private static ResponseEntity.BodyBuilder attachment(String fileName, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
