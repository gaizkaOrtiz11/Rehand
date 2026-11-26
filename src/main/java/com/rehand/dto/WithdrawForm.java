package com.rehand.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Withdraw form (POST /wallet/withdraw). The upper limit is the current balance, checked by the controller
 * (it needs the database) and again by {@code WalletService}. Bank details are simulated: validated, then discarded.
 */
public class WithdrawForm {

    public static final String MIN = "1.00";

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = MIN, message = "The minimum withdrawal is 1.00 euro.")
    @Digits(integer = 8, fraction = 2, message = "Use at most two decimals, for example 20.50.")
    private BigDecimal amount;

    @NotBlank(message = "Enter the account holder's name.")
    @Size(max = 100, message = "Use at most 100 characters.")
    private String accountHolder;

    @NotBlank(message = "Enter your IBAN.")
    @Size(max = 42, message = "An IBAN has at most 34 characters.")
    @Iban
    private String iban;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = FormText.trimToNull(accountHolder);
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = FormText.trimToNull(iban);
    }

    @Override
    public String toString() {
        return "WithdrawForm[amount=" + amount + "]";
    }
}
