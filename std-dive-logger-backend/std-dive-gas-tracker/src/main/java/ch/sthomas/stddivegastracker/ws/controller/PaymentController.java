package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.payment.Payment;
import ch.sthomas.stddivegastracker.model.payment.PaymentInput;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.BalanceService;
import ch.sthomas.stddivegastracker.service.PaymentService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API.md §5 "Balances and payments". */
@RestController
@RequestMapping("/v1/teams/{teamId}")
public final class PaymentController {

    private final BalanceService balanceService;
    private final PaymentService paymentService;

    public PaymentController(
            final BalanceService balanceService, final PaymentService paymentService) {
        this.balanceService = balanceService;
        this.paymentService = paymentService;
    }

    @GetMapping("/balances")
    public Balances balances(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return balanceService.balances(teamId, user);
    }

    @GetMapping("/payments")
    public List<Payment> list(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return paymentService.list(teamId, user);
    }

    @PostMapping("/payments")
    public Payment record(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final PaymentInput request) {
        return paymentService.record(teamId, user, request);
    }

    @DeleteMapping("/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long paymentId) {
        paymentService.delete(teamId, paymentId, user);
    }
}
