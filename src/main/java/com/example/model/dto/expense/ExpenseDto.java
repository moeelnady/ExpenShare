package com.example.model.dto.expense;

import com.example.model.entity.SplitType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Serdeable
@Data
@Builder
public class ExpenseDto {
    private Long expenseId;
    private Long groupId;
    private Long paidBy;
    private BigDecimal amount;
    private String description;
    private List<ShareDto> split;
    private LocalDateTime createdAt;
}
