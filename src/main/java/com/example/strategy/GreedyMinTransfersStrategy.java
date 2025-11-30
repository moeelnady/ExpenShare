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
public class GreedyMinTransfersStrategy implements SettlementStrategy{
    @Override
    public List<SettlementSuggestion> suggestSettlements(List<UserBalance> balances, BigDecimal roundTo) {
        List<UserBalance> work = balances.stream()
                .map(b -> new UserBalance(b.getUserId(), b.getBalance()))
                .toList();
        List<UserBalance> payers = new ArrayList<>();
        List<UserBalance> receivers = new ArrayList<>();
        for (UserBalance b : work) {
            if (b.getBalance().compareTo(BigDecimal.ZERO) > 0) payers.add(b);
            else if (b.getBalance().compareTo(BigDecimal.ZERO) < 0) receivers.add(b);
        }
        payers.sort(Comparator.comparing(UserBalance::getBalance).reversed());
        receivers.sort(Comparator.comparing(UserBalance::getBalance));
        List<SettlementSuggestion> suggestions = new ArrayList<>();
        int i = 0;
        int j = 0;
        while(i< payers.size() && j < receivers.size()){
            UserBalance payer = payers.get(i);
            UserBalance receiver = receivers.get(j);
            BigDecimal receiverNeeded = receiver.getBalance().negate();
            BigDecimal transferAmount = payer.getBalance().min(receiverNeeded);
            if (roundTo != null) {
                transferAmount = transferAmount
                        .divide(roundTo, 0, RoundingMode.HALF_UP)
                        .multiply(roundTo);
            }
            if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
                suggestions.add(new SettlementSuggestion(
                        payer.getUserId(),
                        receiver.getUserId(),
                        transferAmount));
                // update balances
                payer.setBalance(payer.getBalance().subtract(transferAmount));
                receiver.setBalance(receiver.getBalance().add(transferAmount));
            }

            if (payer.getBalance().compareTo(BigDecimal.ZERO) == 0) i++;
            if (receiver.getBalance().compareTo(BigDecimal.ZERO) == 0) j++;


        }
        return suggestions;
    }
}
