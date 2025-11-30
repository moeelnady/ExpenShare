package com.example.strategy;

import com.example.model.dto.settlement.SettlementSuggestion;
import com.example.model.entity.UserBalance;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class SmallestAmountsFirstStrategy implements SettlementStrategy{
    @Override
    public List<SettlementSuggestion> suggestSettlements(List<UserBalance> balances, BigDecimal roundTo) {
        List<UserBalance> work = cloneBalances(balances);
        work.sort(Comparator.comparing(b -> b.getBalance().abs()));

        List<SettlementSuggestion> suggestions = new ArrayList<>();

        for (UserBalance payer : work) {
            if (!isPayer(payer)) continue;

            settlePayer(payer, work, roundTo, suggestions);
        }

        return suggestions;
    }
    private List<UserBalance> cloneBalances(List<UserBalance> balances) {
        return balances.stream()
                .map(b -> new UserBalance(b.getUserId(), b.getBalance()))
                .collect(Collectors.toList());
    }
    private boolean isPayer(UserBalance ub) {
        return ub.getBalance().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isReceiver(UserBalance ub) {
        return ub.getBalance().compareTo(BigDecimal.ZERO) < 0;
    }
    private void settlePayer(UserBalance payer,
                             List<UserBalance> work,
                             BigDecimal roundTo,
                             List<SettlementSuggestion> suggestions) {

        for (UserBalance receiver : work) {
            if (!isReceiver(receiver)) continue;
            if (payer.getUserId().equals(receiver.getUserId())) continue;

            BigDecimal transferAmount = calculateTransfer(payer, receiver, roundTo);
            if (transferAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            applySettlement(payer, receiver, transferAmount, suggestions);

            if (payer.getBalance().compareTo(BigDecimal.ZERO) == 0) break;
        }
    }
    private BigDecimal calculateTransfer(UserBalance payer,
                                         UserBalance receiver,
                                         BigDecimal roundTo) {

        BigDecimal receiverNeeds = receiver.getBalance().negate();
        BigDecimal amount = payer.getBalance().min(receiverNeeds);

        return roundTo != null
                ? round(amount, roundTo)
                : amount;
    }
    private BigDecimal round(BigDecimal amount, BigDecimal roundTo) {
        return amount
                .divide(roundTo, 0, RoundingMode.HALF_UP)
                .multiply(roundTo);
    }
    private void applySettlement(UserBalance payer,
                                 UserBalance receiver,
                                 BigDecimal amount,
                                 List<SettlementSuggestion> suggestions) {

        suggestions.add(new SettlementSuggestion(
                payer.getUserId(),
                receiver.getUserId(),
                amount
        ));

        payer.setBalance(payer.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
    }



}
