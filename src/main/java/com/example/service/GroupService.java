package com.example.service;

import com.example.exception.NotFoundException;
import com.example.model.dto.expense.ShareDto;
import com.example.model.dto.group.AddMembersResponse;
import com.example.model.dto.group.CreateGroupRequest;
import com.example.model.dto.group.GroupBalanceResponse;
import com.example.model.dto.group.GroupDto;
import com.example.model.dto.settlement.GroupSettlementPageResponse;
import com.example.model.dto.settlement.SettlementItem;
import com.example.model.dto.settlement.SettlementSuggestion;
import com.example.model.dto.settlement.SuggestionResponse;
import com.example.model.entity.*;
import com.example.model.mapper.GroupMapper;
import com.example.model.mapper.SettlementMapper;
import com.example.repository.facade.GroupRepositoryFacade;
import com.example.repository.facade.SettlementRepositoryFacade;
import com.example.repository.facade.UserRepositoryFacade;
import com.example.strategy.SettlementStrategy;
import com.example.strategy.SettlementStrategyFactory;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepositoryFacade groupRepositoryFacade;
    private final UserRepositoryFacade userRepositoryFacade;
    private final GroupMapper groupMapper;
    private final SettlementRepositoryFacade settlementRepositoryFacade;
    private final SettlementMapper settlementMapper;
    private final SettlementStrategyFactory strategyFactory;
    @Transactional
    public GroupDto createGroup(CreateGroupRequest request){
        if (!groupRepositoryFacade.usersExist(request.getMembers())) {
            throw new NotFoundException("One or more users not found");
        }
        GroupEntity group = groupMapper.toEntity(request);
        GroupEntity savedGroup = groupRepositoryFacade.save(group);

        List<UserEntity> users = userRepositoryFacade.getAllMembersById(request.getMembers());
        Set<GroupMemberEntity> members = new HashSet<>();
        for (UserEntity user : users) {
            GroupMemberEntity gm = new GroupMemberEntity();
            gm.setGroup(savedGroup);
            gm.setUser(user);
            gm.setAddedAt(LocalDateTime.now());
            members.add(gm);
        }
        savedGroup.setMembers(members);

        // Save group again with members (cascade set of members to their table)
        GroupEntity savedWithMembers = groupRepositoryFacade.save(savedGroup);

        List<Long> memberIds = savedWithMembers.getMembers().stream()
                .map(gm -> gm.getUser().getId())
                .toList();

        return groupMapper.toDto(savedWithMembers, memberIds);
    }

    @Transactional
    public GroupDto getGroup(Long id) {
       GroupEntity entity= groupRepositoryFacade.getGroupOrThrow(id);
        List<Long> members = entity.getMembers().stream()
                .map(m -> m.getUser().getId())
                .toList();
       return groupMapper.toDto(entity,members);
    }

    @Transactional
    public AddMembersResponse addMembers(Long groupId, List<Long> userIds) {
        GroupEntity group = groupRepositoryFacade.getGroupOrThrow(groupId);

        if (!groupRepositoryFacade.usersExist(userIds)) {
            throw new NotFoundException("One or more users not found");
        }

        List<Long> added = groupRepositoryFacade.addAll(groupId, userIds);

        int totalMembers = group.getMembers().size();

        return new AddMembersResponse(groupId, added, totalMembers);
    }

    @Transactional
    public GroupBalanceResponse getGroupBalances(Long groupId, Instant snapshot) {
        GroupEntity group = groupRepositoryFacade.getGroupOrThrow(groupId);
        Instant effectiveSnapshot = (snapshot != null) ? snapshot : Instant.now();
        Map<Long, BigDecimal> balances = getBalancesByGroupId(groupId);
        List<ShareDto> balanceDtos = balances.entrySet().stream()
                .map(e -> new ShareDto(e.getKey(), e.getValue()))
                .toList();
        return new GroupBalanceResponse(group.getId(), balanceDtos, effectiveSnapshot);

    }

    @Transactional
    public GroupSettlementPageResponse listGroupSettlements(
            Long groupId,
            Optional<Status> status,
            Optional<Long> fromUserId,
            Optional<Long> toUserId,
            int page,
            int size
    ) {
        Pageable pageable = Pageable.from(page, Math.min(size, 100));

        Page<SettlementEntity> results = settlementRepositoryFacade.findSettlementByFilters(
                groupId,
                status.orElse(null),
                fromUserId.orElse(null),
                toUserId.orElse(null),
                pageable
        );

        List<SettlementItem> items = results.getContent()
                .stream()
                .map(settlementMapper::toItem)
                .toList();
        return GroupSettlementPageResponse.builder()
                .groupId(groupId)
                .items(items)
                .page(page)
                .size(size)
                .total((int) results.getTotalSize())
                .build();
    }
    @Transactional
    public SuggestionResponse suggest(Long groupId, SettlementStrategyType type, BigDecimal roundTo){
        Map<Long, BigDecimal> balances = getBalancesByGroupId(groupId);
        SettlementStrategy strategy = strategyFactory.getStrategy(type);
        List<UserBalance> userBalancesList = balances.entrySet().stream()
                .map(
                        e-> new UserBalance(e.getKey(),e.getValue()
                        )).toList();
        List<SettlementSuggestion> suggestions = strategy.suggestSettlements(userBalancesList, roundTo);

        return new SuggestionResponse(groupId, suggestions, suggestions.size(), type);
    }
    public List<GroupEntity> getAllGroups(){
        return groupRepositoryFacade.getAll();
    }
    private Map<Long,BigDecimal> getBalancesByGroupId(Long groupId) {
        GroupEntity group = groupRepositoryFacade.getGroupOrThrow(groupId);
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (ExpenseEntity expense : group.getExpenses()) {
            for (ExpenseShareEntity share : expense.getShares()) {
                balances.merge(
                        share.getUser().getId(),
                        share.getShareAmount(),
                        BigDecimal::add
                );
            }
        }
        return balances;
    }
}
