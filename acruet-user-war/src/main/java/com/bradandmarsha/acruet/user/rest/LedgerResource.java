package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.ledger.EnvelopeBundle;
import com.bradandmarsha.acruet.ledger.LedgerAccount;
import com.bradandmarsha.acruet.ledger.LedgerException;
import com.bradandmarsha.acruet.ledger.LedgerService;
import com.bradandmarsha.acruet.ledger.LedgerTransaction;
import com.bradandmarsha.acruet.ledger.TransactionType;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ledger JSON API (Phase 8). HTML dashboard lives on {@code /} (Phase 9).
 */
@Path("ledger")
public class LedgerResource {

    private final LedgerService ledgerService = new LedgerService();

    @GET
    @Path("bundles")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listBundles(
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            List<Map<String, Object>> bundles = ledgerService.listBundles(user, includeArchived).stream()
                    .map(this::bundleJson)
                    .collect(Collectors.toList());
            return Response.ok(Map.of("bundles", bundles)).build();
        });
    }

    @POST
    @Path("bundles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createBundle(CreateBundleRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                String encryptedName = body == null ? null : body.encryptedName;
                EnvelopeBundle bundle = ledgerService.createBundle(user, encryptedName);
                return Response.status(Response.Status.CREATED).entity(bundleJson(bundle)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @PUT
    @Path("bundles/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateBundle(
            @PathParam("id") UUID bundleId,
            UpdateBundleRequest body,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                EnvelopeBundle bundle = ledgerService.updateBundleName(user, bundleId, body.encryptedName);
                return Response.ok(bundleJson(bundle)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("bundles/{id}/archive")
    @Produces(MediaType.APPLICATION_JSON)
    public Response archiveBundle(@PathParam("id") UUID bundleId, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                EnvelopeBundle bundle = ledgerService.archiveBundle(user, bundleId);
                return Response.ok(bundleJson(bundle)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @GET
    @Path("accounts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAccounts(
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived,
            @QueryParam("bundleId") UUID bundleId,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                EnvelopeBundle bundle = ledgerService.resolveBundleForRequest(user, bundleId);
                List<Map<String, Object>> accounts = ledgerService
                        .listAccounts(user, includeArchived, bundle.id())
                        .stream()
                        .map(this::accountJson)
                        .collect(Collectors.toList());
                return Response.ok(Map.of(
                        "bundleId", bundle.id().toString(),
                        "accounts", accounts,
                        "accountCount", bundle.ledgerAccountCount(),
                        "accountLimit", bundle.ledgerAccountLimit())).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("accounts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAccount(CreateAccountRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                UUID bundleId = parseUuid(body == null ? null : body.bundleId);
                LedgerAccount account = ledgerService.createAccount(user, body.encryptedName, bundleId);
                return Response.status(Response.Status.CREATED).entity(accountJson(account)).build();
            } catch (IllegalArgumentException exception) {
                return badRequest("Invalid bundle id.");
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @PUT
    @Path("accounts/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAccount(
            @PathParam("id") UUID accountId,
            UpdateAccountRequest body,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                LedgerAccount account = ledgerService.updateAccountName(user, accountId, body.encryptedName);
                return Response.ok(accountJson(account)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("accounts/{id}/archive")
    @Produces(MediaType.APPLICATION_JSON)
    public Response archiveAccount(@PathParam("id") UUID accountId, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                LedgerAccount account = ledgerService.archiveAccount(user, accountId);
                return Response.ok(accountJson(account)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @GET
    @Path("transactions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTransactions(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("accountId") UUID accountId,
            @QueryParam("bundleId") UUID bundleId,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                LocalDate fromDate = from == null || from.isBlank()
                        ? LocalDate.now().minusYears(10)
                        : LocalDate.parse(from);
                LocalDate toDate = to == null || to.isBlank() ? LocalDate.now().plusDays(1) : LocalDate.parse(to);
                List<Map<String, Object>> transactions = ledgerService
                        .listTransactions(user, fromDate, toDate, accountId, bundleId)
                        .stream()
                        .map(this::transactionJson)
                        .collect(Collectors.toList());
                return Response.ok(Map.of("transactions", transactions)).build();
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("transactions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTransaction(CreateTransactionRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                TransactionType type = TransactionType.valueOf(body.transactionType);
                LocalDate date = LocalDate.parse(body.transactionDate);
                UUID bundleId = parseUuid(body == null ? null : body.bundleId);
                LedgerTransaction transaction = ledgerService.createTransaction(
                        user,
                        type,
                        date,
                        body.encryptedPayload,
                        body.accountIds.stream().map(UUID::fromString).toList(),
                        bundleId);
                return Response.status(Response.Status.CREATED).entity(transactionJson(transaction)).build();
            } catch (IllegalArgumentException exception) {
                return badRequest("Invalid transaction type or date.");
            } catch (LedgerException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    private Response withUser(HttpServletRequest request, java.util.function.Function<AcruetUser, Response> action) {
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (user.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (!user.get().keySetupComplete()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Complete encryption key setup first."))
                    .build();
        }
        return action.apply(user.get());
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", message)).build();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private Map<String, Object> bundleJson(EnvelopeBundle bundle) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", bundle.id().toString());
        json.put("status", bundle.status().name());
        json.put("defaultBundle", bundle.defaultBundle());
        if (bundle.encryptedName() != null) {
            json.put("encryptedName", EncryptedBlob.encode(bundle.encryptedName()));
        }
        json.put("accountCount", bundle.ledgerAccountCount());
        json.put("accountLimit", bundle.ledgerAccountLimit());
        json.put("transactionCount", bundle.transactionCount());
        json.put("createdAt", bundle.createdAt().toString());
        json.put("updatedAt", bundle.updatedAt().toString());
        if (bundle.archivedAt() != null) {
            json.put("archivedAt", bundle.archivedAt().toString());
        }
        return json;
    }

    private Map<String, Object> accountJson(LedgerAccount account) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", account.id().toString());
        json.put("bundleId", account.bundleId().toString());
        json.put("status", account.status().name());
        json.put("encryptedName", EncryptedBlob.encode(account.encryptedName()));
        json.put("createdAt", account.createdAt().toString());
        json.put("updatedAt", account.updatedAt().toString());
        if (account.archivedAt() != null) {
            json.put("archivedAt", account.archivedAt().toString());
        }
        return json;
    }

    private Map<String, Object> transactionJson(LedgerTransaction transaction) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", transaction.id().toString());
        json.put("bundleId", transaction.bundleId().toString());
        json.put("transactionType", transaction.transactionType().name());
        json.put("transactionDate", transaction.transactionDate().toString());
        json.put("encryptedPayload", EncryptedBlob.encode(transaction.encryptedPayload()));
        json.put("accountIds", transaction.accountIds().stream().map(UUID::toString).toList());
        json.put("createdAt", transaction.createdAt().toString());
        return json;
    }

    public static class CreateBundleRequest {
        public String encryptedName;
    }

    public static class UpdateBundleRequest {
        public String encryptedName;
    }

    public static class CreateAccountRequest {
        public String encryptedName;
        public String bundleId;
    }

    public static class UpdateAccountRequest {
        public String encryptedName;
    }

    public static class CreateTransactionRequest {
        public String transactionType;
        public String transactionDate;
        public String encryptedPayload;
        public List<String> accountIds;
        public String bundleId;
    }
}
