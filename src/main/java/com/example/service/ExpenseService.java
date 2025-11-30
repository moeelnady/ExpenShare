package com.example.service;

import com.example.event.KafkaProducer;
import com.example.event.model.EventMessage;
import com.example.exception.ValidationException;
import com.example.model.dto.expense.CreateExpenseRequest;
import com.example.model.dto.expense.ExpenseDto;
import com.example.model.dto.expense.ShareDto;
import com.example.model.dto.expense.ShareRequest;
import com.example.model.entity.ExpenseEntity;
import com.example.model.entity.ExpenseShareEntity;
import com.example.model.entity.GroupEntity;
import com.example.model.entity.UserEntity;
import com.example.model.mapper.ExpenseMapper;
import com.example.repository.GroupMemberRepository;
import com.example.repository.facade.ExpenseRepositoryFacade;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepositoryFacade expenseRepositoryFacade;
    private final GroupRepositoryFacade groupRepositoryFacade;
    private final UserRepositoryFacade userRepositoryFacade;
    private final ExpenseMapper expenseMapper;
    private final KafkaProducer kafkaProducer;
    @Transactional
    public ExpenseDto addExpense(CreateExpenseRequest req){
        GroupEntity group = groupRepositoryFacade.getGroupOrThrow(req.getGroupId());
        UserEntity paidBy = userRepositoryFacade.getOrThrow(req.getPaidBy());
        boolean isMember = groupRepositoryFacade.isMember(group.getId(), paidBy.getId());
        if (!isMember) {
            throw new ValidationException("PaidBy user is not a member of this group");
        }
        ExpenseEntity expense = expenseMapper.toEntity(req);
        expense.setGroup(group);
        expense.setPaidBy(paidBy);
        expense.setCreatedAt(LocalDateTime.now());
        List<ExpenseShareEntity> shares = buildShares(expense, req, group);
        ExpenseEntity saved = expenseRepositoryFacade.saveWithShares(expense, shares);
        List<ShareDto> shareDtos = shares.stream()
                .map(s -> new ShareDto(s.getUser().getId(), s.getShareAmount()))
                .toList();
        kafkaProducer.publishExpenseAdded(EventMessage.of(Map.of(
                "expenseId", saved.getId(),
                "groupId", saved.getGroup().getId(),
                "paidBy", saved.getPaidBy().getId(),
                "amount", saved.getAmount(),
                "description", saved.getDescription()
        )));


        return expenseMapper.toDto(saved, shareDtos);

    }
    private List<ExpenseShareEntity> buildShares(ExpenseEntity expense,
                                                 CreateExpenseRequest req,
                                                 GroupEntity group) {

        return switch (req.getSplitType()) {
            case EQUAL   -> buildEqualShares(expense, req, group);
            case EXACT   -> buildExactShares(expense, req);
            case PERCENT -> buildPercentShares(expense, req);
        };
    }
    private List<ExpenseShareEntity> buildEqualShares(
            ExpenseEntity expense,
            CreateExpenseRequest req,
            GroupEntity group) {

        List<Long> userIds = resolveParticipants(req, group);
        BigDecimal total = req.getAmount();
        BigDecimal perHead = total.divide(BigDecimal.valueOf(userIds.size()), 2, RoundingMode.HALF_UP);

        List<ExpenseShareEntity> shares = new ArrayList<>();
        for (Long uid : userIds) {
            BigDecimal shareValue = uid.equals(req.getPaidBy())
                    ? perHead.subtract(total)
                    : perHead;

            shares.add(createShare(expense, userRepositoryFacade.getOrThrow(uid), shareValue));
        }
        return shares;
    }
    private List<ExpenseShareEntity> buildExactShares(ExpenseEntity expense,
                                                      CreateExpenseRequest req) {

        validateExactSum(req);

        List<ExpenseShareEntity> shares = new ArrayList<>();
        for (ShareRequest sr : req.getShares()) {
            shares.add(createShare(
                    expense,
                    userRepositoryFacade.getOrThrow(sr.getUserId()),
                    sr.getAmount()
            ));
        }
        return shares;
    }
    private List<ExpenseShareEntity> buildPercentShares(ExpenseEntity expense,
                                                        CreateExpenseRequest req) {

        validatePercentSum(req);

        BigDecimal total = req.getAmount();
        List<ExpenseShareEntity> shares = new ArrayList<>();

        for (ShareRequest sr : req.getShares()) {
            BigDecimal value = total
                    .multiply(BigDecimal.valueOf(sr.getPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            shares.add(createShare(
                    expense,
                    userRepositoryFacade.getOrThrow(sr.getUserId()),
                    value
            ));
        }
        return shares;
    }
    private List<Long> resolveParticipants(CreateExpenseRequest req, GroupEntity group) {
        if (req.getParticipants() != null && !req.getParticipants().isEmpty()) {
            return req.getParticipants();
        }
        return groupRepositoryFacade.findUserIdsByGroupId(group.getId());
    }
    private void validateExactSum(CreateExpenseRequest req) {
        BigDecimal total = req.getAmount();
        BigDecimal sum = req.getShares().stream()
                .map(ShareRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(total) != 0) {
            throw new ValidationException("Split amounts must total " + total);
        }
    }

    private void validatePercentSum(CreateExpenseRequest req) {
        int percentSum = req.getShares().stream()
                .mapToInt(ShareRequest::getPercent)
                .sum();

        if (percentSum != 100) {
            throw new ValidationException("Split percentages must total 100");
        }
    }
    private ExpenseShareEntity createShare(ExpenseEntity expense,
                                           UserEntity user,
                                           BigDecimal amount) {
        ExpenseShareEntity share = new ExpenseShareEntity();
        share.setExpense(expense);
        share.setUser(user);
        share.setShareAmount(amount);
        return share;
    }







}
